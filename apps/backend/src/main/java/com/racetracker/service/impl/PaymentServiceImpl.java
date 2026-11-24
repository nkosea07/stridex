package com.racetracker.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.racetracker.config.RaceTrackerProperties;
import com.racetracker.dto.participant.PaymentDto;
import com.racetracker.dto.participant.ExternalRefundAttemptDto;
import com.racetracker.exception.ApiException;
import com.racetracker.exception.ConflictException;
import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.exception.ServiceUnavailableException;
import com.racetracker.model.Participant;
import com.racetracker.model.Payment;
import com.racetracker.model.PaymentRefund;
import com.racetracker.model.PaymentReversalCase;
import com.racetracker.model.Race;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.PaymentRepository;
import com.racetracker.repository.PaymentReversalCaseRepository;
import com.racetracker.repository.UserRepository;
import com.racetracker.service.PaymentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.List;

@Slf4j
@Service
public class PaymentServiceImpl implements PaymentService {

    private static final String PROMO_REFERENCE_PREFIX = "PROMO:";
    private static final String PROMO_PROVIDER = "PROMO_CODE";

    private static final String PROVIDER = "PESEPAY";

    private final PaymentRepository paymentRepository;
    private final ParticipantRepository participantRepository;
    private final PesepayClient pesepayClient;
    private final RaceTrackerProperties properties;
    private final ObjectMapper objectMapper;
    private final com.racetracker.service.PromoCodeService promoCodeService;
    private final com.racetracker.service.NotificationService notificationService;
    private final com.racetracker.repository.PaymentRefundRepository paymentRefundRepository;
    private final UserRepository userRepository;
    private final PaymentReversalCaseRepository reversalCaseRepository;
    private final TransactionTemplate transactionTemplate;

    public PaymentServiceImpl(
        PaymentRepository paymentRepository,
        ParticipantRepository participantRepository,
        PesepayClient pesepayClient,
        RaceTrackerProperties properties,
        ObjectMapper objectMapper,
        com.racetracker.service.PromoCodeService promoCodeService,
        com.racetracker.service.NotificationService notificationService,
        com.racetracker.repository.PaymentRefundRepository paymentRefundRepository,
        UserRepository userRepository,
        PaymentReversalCaseRepository reversalCaseRepository,
        PlatformTransactionManager transactionManager
    ) {
        this.paymentRepository = paymentRepository;
        this.participantRepository = participantRepository;
        this.pesepayClient = pesepayClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.promoCodeService = promoCodeService;
        this.notificationService = notificationService;
        this.paymentRefundRepository = paymentRefundRepository;
        this.userRepository = userRepository;
        this.reversalCaseRepository = reversalCaseRepository;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public PaymentDto startCheckout(UUID raceId, UUID participantId, String promoCode,
                                    String actorEmail, boolean athleteSelfService) {
        CheckoutDraft draft = transactionTemplate.execute(status ->
            stageCheckout(raceId, participantId, promoCode, actorEmail, athleteSelfService));
        if (draft.coveredByPromo() != null) {
            return draft.coveredByPromo();
        }
        RaceTrackerProperties.Payments payments = properties.getPayments();

        PesepayClient.InitiateResult result;
        try {
            result = pesepayClient.initiate(
                draft.payable().doubleValue(),
                draft.currency(),
                "Race registration - " + draft.raceName(),
                payments.getResultUrl(),
                payments.getReturnUrl(),
                draft.merchantReference()
            );
        } catch (RuntimeException ex) {
            transactionTemplate.executeWithoutResult(status -> markCheckoutFailed(draft.paymentId()));
            throw ex;
        }

        return transactionTemplate.execute(status -> recordCheckoutStarted(raceId, participantId, draft.paymentId(), result));
    }

    private CheckoutDraft stageCheckout(UUID raceId, UUID participantId, String promoCode,
                                        String actorEmail, boolean athleteSelfService) {
        Participant participant = participantRepository.findByRaceIdAndId(raceId, participantId)
            .orElseThrow(() -> new ResourceNotFoundException("Participant not found"));
        if (athleteSelfService && (participant.getUser() == null
            || participant.getUser().getEmail() == null
            || actorEmail == null
            || !participant.getUser().getEmail().equalsIgnoreCase(actorEmail))) {
            throw new AccessDeniedException("You can only start checkout for your own participant record");
        }
        if (participant.getPaymentStatus() == Participant.PaymentStatus.REFUNDED) {
            throw new ConflictException("Refunded registrations cannot start another checkout", "REFUND_STATE_CONFLICT");
        }
        if (participant.getPaymentStatus() == Participant.PaymentStatus.PAID) {
            throw new ConflictException("This registration has already been paid", "PAYMENT_ALREADY_COMPLETED");
        }
        if (participant.getPaymentStatus() == Participant.PaymentStatus.WAIVED) {
            throw new ConflictException("The registration fee for this participant was waived", "PAYMENT_WAIVED");
        }
        if (participant.getStatus() == Participant.ParticipantStatus.REJECTED
            || participant.getStatus() == Participant.ParticipantStatus.WAITLISTED
            || participant.getStatus() == Participant.ParticipantStatus.WITHDRAWN
            || participant.getStatus() == Participant.ParticipantStatus.DISQUALIFIED) {
            throw new ConflictException("Payment opens once this registration has a confirmed place",
                "PAYMENT_NOT_AVAILABLE_FOR_STATUS");
        }
        Race race = participant.getRace();

        BigDecimal baseAmount = com.racetracker.service.RegistrationFees.current(participant, race);
        if (baseAmount == null || baseAmount.signum() <= 0) {
            throw new ApiException("This race does not require an online payment");
        }

        com.racetracker.service.PromoCodeService.AppliedPromo applied =
            promoCodeService.apply(promoCode, raceId, baseAmount);
        BigDecimal discount = applied == null ? BigDecimal.ZERO : applied.discount();
        BigDecimal payable = baseAmount.subtract(discount);
        String currency = !isBlank(race.getCurrency()) ? race.getCurrency() : properties.getPayments().getCurrency();
        if (payable.signum() <= 0) {
            return new CheckoutDraft(null, BigDecimal.ZERO, currency, race.getName(), null,
                waiveWithPromo(participant, applied, baseAmount, currency));
        }
        RaceTrackerProperties.Payments payments = properties.getPayments();
        if (isBlank(payments.getResultUrl()) || isBlank(payments.getReturnUrl())) {
            throw new ApiException("Payment result/return URLs are not configured");
        }

        String merchantReference = "RACE-" + raceId + "-P-" + participantId + "-" + UUID.randomUUID().toString().substring(0, 8);

        Payment payment = paymentRepository.save(Payment.builder()
            .race(race)
            .participant(participant)
            .provider(PROVIDER)
            .merchantReference(merchantReference)
            .amount(baseAmount)
            .discountAmount(discount)
            .promoCode(applied != null ? applied.code() : null)
            .currency(currency)
            .status(Payment.PaymentStatus.PENDING)
            .build());

        return new CheckoutDraft(payment.getId(), payable, currency, race.getName(), merchantReference, null);
    }

    private PaymentDto waiveWithPromo(Participant participant, com.racetracker.service.PromoCodeService.AppliedPromo applied,
                                      BigDecimal baseAmount, String currency) {
        participant.setPaymentStatus(Participant.PaymentStatus.WAIVED);
        participant.setPaymentReference(PROMO_REFERENCE_PREFIX + applied.code());
        participant.setPaymentAmount(BigDecimal.ZERO);
        participant.setPaymentVerifiedAt(OffsetDateTime.now());
        participantRepository.save(participant);
        promoCodeService.recordUse(applied.promoCodeId());

        PaymentDto dto = new PaymentDto();
        dto.setParticipantId(participant.getId());
        dto.setProvider(PROMO_PROVIDER);
        dto.setStatus(Participant.PaymentStatus.WAIVED.name());
        dto.setAmount(baseAmount);
        dto.setPayableAmount(BigDecimal.ZERO);
        dto.setCurrency(currency);
        dto.setReference(participant.getPaymentReference());
        return dto;
    }

    private PaymentDto recordCheckoutStarted(UUID raceId, UUID participantId, UUID paymentId,
                                             PesepayClient.InitiateResult result) {
        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        payment.setProviderRef(result.referenceNumber());
        payment.setCheckoutUrl(result.redirectUrl());
        payment = paymentRepository.save(payment);

        participantRepository.findByRaceIdAndId(raceId, participantId).ifPresent(participant -> {
            if (participant.getPaymentStatus() == Participant.PaymentStatus.UNPAID) {
                participant.setPaymentStatus(Participant.PaymentStatus.PENDING);
                participantRepository.save(participant);
            }
        });
        return toDto(payment);
    }

    private void markCheckoutFailed(UUID paymentId) {
        paymentRepository.findById(paymentId).ifPresent(payment -> {
            if (payment.getStatus() == Payment.PaymentStatus.PENDING && payment.getProviderRef() == null) {
                payment.setStatus(Payment.PaymentStatus.FAILED);
                paymentRepository.save(payment);
            }
        });
    }

    private record CheckoutDraft(UUID paymentId, BigDecimal payable, String currency, String raceName,
                                 String merchantReference, PaymentDto coveredByPromo) {
    }

    @Override
    @Transactional
    public PaymentDto refund(UUID raceId, UUID participantId, BigDecimal amount, String reason, String actorEmail) {
        throw new ServiceUnavailableException("Automated refunds are unavailable",
            "REFUND_AUTOMATION_UNAVAILABLE");
    }

    @Override
    @Transactional
    public PaymentDto stageExternalRefund(UUID raceId, UUID participantId, String reason, String actorEmail) {
        ExternalRefundAttemptDto attempt = stageExternalRefundAttempt(raceId, participantId, null, reason, actorEmail);
        return toDto(paymentRepository.findById(attempt.paymentId()).orElseThrow());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentDto> eligibleRefundPayments(UUID raceId, UUID participantId) {
        requireParticipant(raceId, participantId);
        return paymentRepository.findByParticipantIdAndStatusOrderByCreatedAtDesc(
            participantId, Payment.PaymentStatus.COMPLETED).stream()
            .filter(payment -> paymentRefundRepository.findFirstByPaymentIdAndStatus(
                payment.getId(), PaymentRefund.RefundStatus.REFUNDED).isEmpty())
            .map(this::toDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> eligibleRefundParticipantIds(UUID raceId) {
        return paymentRepository.findRefundEligibleParticipantIds(raceId);
    }

    @Override
    @Transactional
    public ExternalRefundAttemptDto stageExternalRefundAttempt(UUID raceId, UUID participantId, UUID paymentId,
                                                               String reason, String actorEmail) {
        if (reason == null || reason.isBlank()) {
            throw new ApiException("A refund reason is required", "REFUND_REASON_REQUIRED");
        }
        requireParticipant(raceId, participantId);
        Payment payment = lockRefundPayment(participantId, paymentId);
        if (payment.getStatus() != Payment.PaymentStatus.COMPLETED) {
            throw new ConflictException("No completed payment found to refund", "REFUND_PAYMENT_NOT_COMPLETED");
        }
        if (paymentRefundRepository
            .findFirstByPaymentIdAndStatus(payment.getId(), PaymentRefund.RefundStatus.REFUNDED)
            .isPresent()) {
            throw new ConflictException("Payment was already refunded", "REFUND_ALREADY_COMPLETED");
        }
        if (paymentRefundRepository.findFirstByPaymentIdAndStatus(payment.getId(), PaymentRefund.RefundStatus.PENDING)
            .isPresent()) {
            throw new ConflictException("A refund attempt is already pending", "REFUND_ATTEMPT_ALREADY_PENDING");
        }
        BigDecimal refundAmount = payableAmount(payment);
        if (refundAmount == null || refundAmount.signum() <= 0) {
            throw new ConflictException("Refund amount must be positive", "REFUND_AMOUNT_INVALID");
        }
        PaymentRefund refund = PaymentRefund.builder()
            .payment(payment)
            .amount(refundAmount)
            .currency(payment.getCurrency())
            .reason(reason)
            .status(PaymentRefund.RefundStatus.PENDING)
            .requestedBy(resolveActorId(actorEmail))
            .build();
        PaymentRefund saved = paymentRefundRepository.save(refund);
        return toAttemptDto(saved);
    }

    @Override
    @Transactional
    public PaymentDto reconcileExternalRefund(UUID raceId, UUID participantId, String providerReference,
                                              String reason, boolean refunded, String actorEmail) {
        requireParticipant(raceId, participantId);
        List<PaymentRefund> pending = paymentRefundRepository.findByPaymentParticipantIdAndStatus(
            participantId, PaymentRefund.RefundStatus.PENDING);
        if (pending.size() != 1) {
            if (pending.isEmpty() && providerReference != null && reason != null) {
                PaymentRefund prior = paymentRefundRepository.findByProviderRef(providerReference.trim()).orElse(null);
                PaymentRefund.RefundStatus expected = refunded
                    ? PaymentRefund.RefundStatus.REFUNDED : PaymentRefund.RefundStatus.NOT_REFUNDED;
                if (prior != null && prior.getPayment().getParticipant().getId().equals(participantId)
                    && prior.getStatus() == expected && reason.trim().equals(prior.getReason())) {
                    return toDto(prior.getPayment());
                }
            }
            throw new ConflictException("Select a refund attempt", "REFUND_ATTEMPT_SELECTION_REQUIRED");
        }
        ExternalRefundAttemptDto result = reconcileExternalRefundAttempt(raceId, participantId, pending.get(0).getId(),
            providerReference, reason, refunded, actorEmail);
        return toDto(paymentRepository.findById(result.paymentId()).orElseThrow());
    }

    @Override
    @Transactional(readOnly = true)
    public ExternalRefundAttemptDto getExternalRefundAttempt(UUID raceId, UUID participantId, UUID attemptId) {
        requireParticipant(raceId, participantId);
        PaymentRefund attempt = paymentRefundRepository.findById(attemptId)
            .orElseThrow(() -> new ResourceNotFoundException("Refund attempt not found"));
        if (!attempt.getPayment().getParticipant().getId().equals(participantId)) {
            throw new ResourceNotFoundException("Refund attempt not found");
        }
        return toAttemptDto(attempt);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExternalRefundAttemptDto> listExternalRefundAttempts(UUID raceId, UUID participantId) {
        requireParticipant(raceId, participantId);
        return paymentRefundRepository.findByPaymentParticipantIdOrderByCreatedAtDesc(
            participantId, PageRequest.of(0, 50)).map(this::toAttemptDto).getContent();
    }

    @Override
    @Transactional
    public ExternalRefundAttemptDto reconcileExternalRefundAttempt(UUID raceId, UUID participantId, UUID attemptId,
                                                                    String providerReference, String reason,
                                                                    boolean refunded, String actorEmail) {
        if (providerReference == null || providerReference.isBlank() || reason == null || reason.isBlank()) {
            throw new ApiException("A provider receipt or case reference and reason are required",
                "REFUND_EVIDENCE_REQUIRED");
        }
        String reference = providerReference.trim();
        Participant participant = requireParticipant(raceId, participantId);
        UUID attemptPaymentId = paymentRefundRepository.findPaymentIdByAttemptId(attemptId)
            .orElseThrow(() -> new ResourceNotFoundException("Refund attempt not found"));
        Payment payment = paymentRepository.findByIdForUpdate(attemptPaymentId)
            .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        if (!payment.getParticipant().getId().equals(participantId)) {
            throw new ResourceNotFoundException("Refund attempt not found");
        }
        PaymentRefund attempt = paymentRefundRepository.findById(attemptId)
            .orElseThrow(() -> new ResourceNotFoundException("Refund attempt not found"));
        PaymentRefund.RefundStatus outcome = refunded
            ? PaymentRefund.RefundStatus.REFUNDED : PaymentRefund.RefundStatus.NOT_REFUNDED;
        if (attempt.getStatus() != PaymentRefund.RefundStatus.PENDING) {
            if (attempt.getStatus() == outcome && reference.equals(attempt.getProviderRef())
                && reason.trim().equals(attempt.getReason())) {
                return toAttemptDto(attempt);
            }
            throw new ConflictException("Refund attempt already resolved", "REFUND_ATTEMPT_CONFLICT");
        }
        BigDecimal payable = payableAmount(payment);
        if (refunded && (attempt.getAmount() == null || payable == null
            || attempt.getAmount().compareTo(payable) != 0)) {
            throw new ConflictException("Legacy partial refund attempts require finance reconciliation",
                "REFUND_AMOUNT_MISMATCH");
        }
        PaymentRefund prior = paymentRefundRepository.findByProviderRef(reference).orElse(null);
        if (prior != null) {
            throw new ConflictException("Refund reference has a conflicting outcome", "REFUND_REFERENCE_CONFLICT");
        }
        if (payment.getStatus() != Payment.PaymentStatus.COMPLETED
            && !(refunded && payment.getStatus() == Payment.PaymentStatus.REFUNDED)) {
            throw new ConflictException("Payment is not completed", "REFUND_PAYMENT_NOT_COMPLETED");
        }
        attempt.setStatus(outcome);
        attempt.setProviderRef(reference);
        attempt.setReason(reason.trim());
        attempt.setCompletedAt(OffsetDateTime.now());
        try {
            paymentRefundRepository.saveAndFlush(attempt);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("Refund reference has a conflicting outcome", "REFUND_REFERENCE_CONFLICT");
        }

        if (refunded) {
            payment.setStatus(Payment.PaymentStatus.REFUNDED);
            paymentRepository.save(payment);
            participant.setPaymentStatus(Participant.PaymentStatus.REFUNDED);
            participantRepository.save(participant);
            notifyPayment(participant, com.racetracker.model.Notification.NotificationType.RACE_UPDATE,
                "payment.refunded", raceName(participant));
        }
        reversalCaseRepository.findById(payment.getId()).ifPresent(reversal -> {
            reversal.setResolvedAt(OffsetDateTime.now());
            reversal.setResolutionReference(reference);
            reversalCaseRepository.save(reversal);
        });
        return toAttemptDto(attempt);
    }

    private Participant requireParticipant(UUID raceId, UUID participantId) {
        return participantRepository.findByRaceIdAndId(raceId, participantId)
            .orElseThrow(() -> new ResourceNotFoundException("Participant not found"));
    }

    private Payment lockRefundPayment(UUID participantId, UUID selectedId) {
        List<Payment> eligible = paymentRepository.findByParticipantIdAndStatusOrderByCreatedAtDesc(
            participantId, Payment.PaymentStatus.COMPLETED);
        if (selectedId == null && eligible.size() != 1) {
            throw new ConflictException("Select a completed payment", "REFUND_PAYMENT_SELECTION_REQUIRED");
        }
        UUID id = selectedId != null ? selectedId : eligible.get(0).getId();
        if (eligible.stream().noneMatch(payment -> payment.getId().equals(id))) {
            throw new ConflictException("Payment is not eligible for refund", "REFUND_PAYMENT_NOT_COMPLETED");
        }
        return paymentRepository.findByIdForUpdate(id)
            .orElseThrow(() -> new ConflictException("No payment found", "REFUND_PAYMENT_NOT_FOUND"));
    }

    private ExternalRefundAttemptDto toAttemptDto(PaymentRefund attempt) {
        Payment payment = attempt.getPayment();
        return new ExternalRefundAttemptDto(attempt.getId(), payment.getId(), attempt.getAmount(),
            attempt.getCurrency(), attempt.getStatus().name(), payment.getProviderRef(),
            attempt.getProviderRef(), attempt.getCreatedAt());
    }

    private BigDecimal payableAmount(Payment payment) {
        if (payment.getAmount() == null) {
            return null;
        }
        BigDecimal discount = payment.getDiscountAmount() == null ? BigDecimal.ZERO : payment.getDiscountAmount();
        return payment.getAmount().subtract(discount);
    }

    private UUID resolveActorId(String actorEmail) {
        if (actorEmail == null) {
            return null;
        }
        return userRepository.findByEmailIgnoreCase(actorEmail).map(user -> user.getId()).orElse(null);
    }

    @Override
    public void handlePesepayCallback(String authorizationHeader, String rawBody) {
        RaceTrackerProperties.Payments payments = properties.getPayments();
        if (isBlank(payments.getIntegrationKey()) || authorizationHeader == null
            || !java.security.MessageDigest.isEqual(
                payments.getIntegrationKey().getBytes(java.nio.charset.StandardCharsets.UTF_8),
                authorizationHeader.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            throw new ApiException("Invalid payment callback authorization");
        }

        String referenceNumber;
        try {
            JsonNode node = objectMapper.readTree(rawBody == null ? "{}" : rawBody);
            referenceNumber = node.hasNonNull("referenceNumber") ? node.get("referenceNumber").asText() : null;
        } catch (Exception ex) {
            throw new ApiException("Invalid payment callback payload");
        }
        if (referenceNumber == null) {
            throw new ApiException("Payment callback missing referenceNumber");
        }

        Payment payment = paymentRepository.findByProviderAndProviderRef(PROVIDER, referenceNumber).orElse(null);
        if (payment == null) {
            // Not a payment we originated; acknowledge silently.
            return;
        }
        if (payment.getStatus() == Payment.PaymentStatus.REFUNDED) {
            return;
        }

        String status = pesepayClient.checkStatus(referenceNumber);
        UUID paymentId = payment.getId();
        transactionTemplate.executeWithoutResult(transaction -> {
            Payment locked = paymentRepository.findByIdForUpdate(paymentId).orElse(null);
            if (locked == null || !PROVIDER.equals(locked.getProvider())
                || !referenceNumber.equals(locked.getProviderRef())
                || locked.getStatus() == Payment.PaymentStatus.REFUNDED) {
                return;
            }
            if (locked.getStatus() == Payment.PaymentStatus.COMPLETED
                && !"REVERSED".equalsIgnoreCase(status == null ? "" : status.trim())) {
                return;
            }
            applyStatus(locked, status);
        });
    }

    private void applyStatus(Payment payment, String transactionStatus) {
        String normalized = transactionStatus == null ? "" : transactionStatus.trim().toUpperCase();
        switch (normalized) {
            case "SUCCESS" -> {
                if (payment.getStatus() == Payment.PaymentStatus.COMPLETED) {
                    return;
                }
                if (payment.getParticipant().getPaymentStatus() == Participant.PaymentStatus.REFUNDED) {
                    log.warn("Ignoring payment success after refund; paymentId={}", payment.getId());
                    return;
                }
                payment.setStatus(Payment.PaymentStatus.COMPLETED);
                payment.setCompletedAt(OffsetDateTime.now());
                paymentRepository.save(payment);

                Participant participant = payment.getParticipant();
                participant.setPaymentStatus(Participant.PaymentStatus.PAID);
                participant.setPaymentReference(payment.getProviderRef());
                java.math.BigDecimal payable = payment.getAmount() == null ? null
                    : payment.getAmount().subtract(payment.getDiscountAmount() == null
                        ? java.math.BigDecimal.ZERO : payment.getDiscountAmount());
                participant.setPaymentAmount(payable);
                participant.setPaymentVerifiedAt(OffsetDateTime.now());
                participantRepository.save(participant);

                if (payment.getPromoCode() != null) {
                    promoCodeService.recordUseByCode(payment.getPromoCode());
                }

                notifyPayment(participant, com.racetracker.model.Notification.NotificationType.RACE_UPDATE,
                    "payment.received", raceName(participant));
            }
            case "FAILED" -> {
                payment.setStatus(Payment.PaymentStatus.FAILED);
                paymentRepository.save(payment);
                reopenUnpaidEntry(payment.getParticipant());
                notifyPayment(payment.getParticipant(), com.racetracker.model.Notification.NotificationType.ALERT,
                    "payment.failed", raceName(payment.getParticipant()));
            }
            case "CANCELLED" -> {
                payment.setStatus(Payment.PaymentStatus.CANCELLED);
                paymentRepository.save(payment);
                reopenUnpaidEntry(payment.getParticipant());
            }
            case "REVERSED" -> {
                if (!reversalCaseRepository.existsById(payment.getId())) {
                    PaymentReversalCase reversal = new PaymentReversalCase();
                    reversal.setPaymentId(payment.getId());
                    reversal.setProviderReference(payment.getProviderRef());
                    reversal.setObservedAt(OffsetDateTime.now());
                    reversalCaseRepository.saveAndFlush(reversal);
                }
                log.warn("Provider reversal needs finance verification; paymentId={}", payment.getId());
            }
            default -> log.info("Pesepay transaction {} still pending with status {}", payment.getProviderRef(), normalized);
        }
    }

    private void reopenUnpaidEntry(Participant participant) {
        if (participant == null || participant.getPaymentStatus() != Participant.PaymentStatus.PENDING
            || paymentRepository.existsByParticipantIdAndStatus(participant.getId(), Payment.PaymentStatus.PENDING)) {
            return;
        }
        participant.setPaymentStatus(Participant.PaymentStatus.UNPAID);
        participantRepository.save(participant);
    }

    private PaymentDto toDto(Payment payment) {
        PaymentDto dto = new PaymentDto();
        dto.setId(payment.getId());
        dto.setParticipantId(payment.getParticipant() != null ? payment.getParticipant().getId() : null);
        dto.setProvider(payment.getProvider());
        dto.setStatus(payment.getStatus() != null ? payment.getStatus().name() : null);
        dto.setAmount(payment.getAmount());
        dto.setPayableAmount(payableAmount(payment));
        dto.setCurrency(payment.getCurrency());
        dto.setReference(payment.getProviderRef());
        dto.setCheckoutUrl(payment.getCheckoutUrl());
        return dto;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void notifyPayment(Participant participant, com.racetracker.model.Notification.NotificationType type,
                               String templateCode, Object... args) {
        if (participant == null || participant.getUser() == null) {
            return;
        }
        try {
            notificationService.notifyLocalized(participant.getUser().getId(), type, templateCode, args,
                notificationData(participant, "PAYMENT"));
        } catch (RuntimeException ex) {
            log.warn("Failed to notify participant {} about payment: {}", participant.getId(), ex.getMessage());
        }
    }

    private String raceName(Participant participant) {
        if (participant == null || participant.getRace() == null || participant.getRace().getName() == null) {
            return "";
        }
        return participant.getRace().getName();
    }

    private static java.util.Map<String, String> notificationData(Participant participant, String kind) {
        java.util.Map<String, String> data = new java.util.LinkedHashMap<>();
        data.put("kind", kind);
        data.put("participantId", participant.getId().toString());
        if (participant.getRace() != null && participant.getRace().getId() != null) {
            data.put("raceId", participant.getRace().getId().toString());
        }
        return data;
    }
}
