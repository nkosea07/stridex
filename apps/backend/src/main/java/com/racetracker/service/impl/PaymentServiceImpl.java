package com.racetracker.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.racetracker.config.RaceTrackerProperties;
import com.racetracker.dto.participant.PaymentDto;
import com.racetracker.exception.ApiException;
import com.racetracker.exception.ConflictException;
import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.exception.ServiceUnavailableException;
import com.racetracker.model.Participant;
import com.racetracker.model.Payment;
import com.racetracker.model.PaymentRefund;
import com.racetracker.model.Race;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.PaymentRepository;
import com.racetracker.repository.UserRepository;
import com.racetracker.service.PaymentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Slf4j
@Service
public class PaymentServiceImpl implements PaymentService {

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
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    @Transactional
    public PaymentDto startCheckout(UUID raceId, UUID participantId, String promoCode,
                                    String actorEmail, boolean athleteSelfService) {
        Participant participant = participantRepository.findByRaceIdAndId(raceId, participantId)
            .orElseThrow(() -> new ResourceNotFoundException("Participant not found"));
        if (athleteSelfService && (participant.getUser() == null
            || participant.getUser().getEmail() == null
            || actorEmail == null
            || !participant.getUser().getEmail().equalsIgnoreCase(actorEmail))) {
            throw new AccessDeniedException("You can only start checkout for your own participant record");
        }
        RaceTrackerProperties.Payments payments = properties.getPayments();
        if (isBlank(payments.getResultUrl()) || isBlank(payments.getReturnUrl())) {
            throw new ApiException("Payment result/return URLs are not configured");
        }
        Race race = participant.getRace();

        BigDecimal baseAmount = participant.getCategory() != null && participant.getCategory().getRegistrationFee() != null
            ? participant.getCategory().getRegistrationFee()
            : race.getRegistrationFee();
        if (baseAmount == null || baseAmount.signum() <= 0) {
            throw new ApiException("This race does not require an online payment");
        }

        com.racetracker.service.PromoCodeService.AppliedPromo applied =
            promoCodeService.apply(promoCode, raceId, baseAmount);
        BigDecimal discount = applied == null ? BigDecimal.ZERO : applied.discount();
        BigDecimal payable = baseAmount.subtract(discount);
        if (payable.signum() <= 0) {
            throw new ApiException("This promo code covers the full registration fee; no online payment is required");
        }

        String currency = !isBlank(race.getCurrency()) ? race.getCurrency() : payments.getCurrency();

        String merchantReference = "RACE-" + raceId + "-P-" + participantId + "-" + UUID.randomUUID().toString().substring(0, 8);

        Payment payment = Payment.builder()
            .race(race)
            .participant(participant)
            .provider(PROVIDER)
            .merchantReference(merchantReference)
            .amount(baseAmount)
            .discountAmount(discount)
            .promoCode(applied != null ? applied.code() : null)
            .currency(currency)
            .status(Payment.PaymentStatus.PENDING)
            .build();
        payment = paymentRepository.save(payment);

        PesepayClient.InitiateResult result = pesepayClient.initiate(
            payable.doubleValue(),
            currency,
            "Race registration - " + race.getName(),
            payments.getResultUrl(),
            payments.getReturnUrl(),
            merchantReference
        );

        payment.setProviderRef(result.referenceNumber());
        payment.setCheckoutUrl(result.redirectUrl());
        payment = paymentRepository.save(payment);

        if (participant.getPaymentStatus() == Participant.PaymentStatus.UNPAID) {
            participant.setPaymentStatus(Participant.PaymentStatus.PENDING);
            participantRepository.save(participant);
        }

        return toDto(payment);
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
        if (reason == null || reason.isBlank()) {
            throw new ApiException("A refund reason is required", "REFUND_REASON_REQUIRED");
        }
        Participant participant = participantRepository.findByRaceIdAndId(raceId, participantId)
            .orElseThrow(() -> new ResourceNotFoundException("Participant not found"));
        Payment payment = lockLatestPayment(participantId);
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
        paymentRefundRepository.save(refund);

        return toDto(payment);
    }

    @Override
    @Transactional
    public PaymentDto reconcileExternalRefund(UUID raceId, UUID participantId, String providerReference,
                                              String reason, boolean refunded, String actorEmail) {
        if (providerReference == null || providerReference.isBlank() || reason == null || reason.isBlank()) {
            throw new ApiException("A provider receipt or case reference and reason are required",
                "REFUND_EVIDENCE_REQUIRED");
        }
        String reference = providerReference.trim();
        Participant participant = participantRepository.findByRaceIdAndId(raceId, participantId)
            .orElseThrow(() -> new ResourceNotFoundException("Participant not found"));
        Payment payment = lockLatestPayment(participantId);
        PaymentRefund.RefundStatus outcome = refunded
            ? PaymentRefund.RefundStatus.REFUNDED : PaymentRefund.RefundStatus.NOT_REFUNDED;
        PaymentRefund attempt = paymentRefundRepository
            .findFirstByPaymentIdAndStatus(payment.getId(), PaymentRefund.RefundStatus.PENDING)
            .orElse(null);
        PaymentRefund prior = paymentRefundRepository.findByProviderRef(reference).orElse(null);
        if (prior != null) {
            if (attempt != null) {
                throw new ConflictException("Reference belongs to an earlier refund attempt", "REFUND_REFERENCE_CONFLICT");
            }
            if (prior.getPayment().getId().equals(payment.getId()) && prior.getStatus() == outcome
                && reason.trim().equals(prior.getReason())) {
                return toDto(payment);
            }
            throw new ConflictException("Refund reference has a conflicting outcome", "REFUND_REFERENCE_CONFLICT");
        }
        if (attempt == null) {
            throw new ConflictException("No pending refund attempt", "REFUND_ATTEMPT_REQUIRED");
        }
        if (payment.getStatus() != Payment.PaymentStatus.COMPLETED
            && !(refunded && payment.getStatus() == Payment.PaymentStatus.REFUNDED)) {
            throw new ConflictException("Payment is not completed", "REFUND_PAYMENT_NOT_COMPLETED");
        }
        if (attempt.getAmount().compareTo(payableAmount(payment)) != 0) {
            throw new ConflictException("Pending refund amount is not the full payable amount", "REFUND_AMOUNT_MISMATCH");
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
                "payment.refunded", "Refund processed", "Your payment for " + raceName(participant) + " has been refunded.");
        }
        return toDto(payment);
    }

    private Payment lockLatestPayment(UUID participantId) {
        UUID paymentId = paymentRepository.findFirstByParticipantIdOrderByCreatedAtDesc(participantId)
            .orElseThrow(() -> new ConflictException("No payment found", "REFUND_PAYMENT_NOT_FOUND")).getId();
        return paymentRepository.findByIdForUpdate(paymentId)
            .orElseThrow(() -> new ConflictException("No payment found", "REFUND_PAYMENT_NOT_FOUND"));
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
        if (isBlank(payments.getIntegrationKey()) || !payments.getIntegrationKey().equals(authorizationHeader)) {
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
                    "payment.received", "Payment received", "Your payment for " + raceName(participant) + " has been received.");
            }
            case "FAILED" -> {
                payment.setStatus(Payment.PaymentStatus.FAILED);
                paymentRepository.save(payment);
                notifyPayment(payment.getParticipant(), com.racetracker.model.Notification.NotificationType.ALERT,
                    "payment.failed", "Payment failed", "Your payment for " + raceName(payment.getParticipant()) + " did not complete.");
            }
            case "CANCELLED" -> {
                payment.setStatus(Payment.PaymentStatus.CANCELLED);
                paymentRepository.save(payment);
            }
            case "REVERSED" -> {
                payment.setStatus(Payment.PaymentStatus.REFUNDED);
                paymentRepository.save(payment);
                Participant participant = payment.getParticipant();
                participant.setPaymentStatus(Participant.PaymentStatus.REFUNDED);
                participantRepository.save(participant);
            }
            default -> log.info("Pesepay transaction {} still pending with status {}", payment.getProviderRef(), normalized);
        }
    }

    private PaymentDto toDto(Payment payment) {
        PaymentDto dto = new PaymentDto();
        dto.setId(payment.getId());
        dto.setParticipantId(payment.getParticipant() != null ? payment.getParticipant().getId() : null);
        dto.setProvider(payment.getProvider());
        dto.setStatus(payment.getStatus() != null ? payment.getStatus().name() : null);
        dto.setAmount(payment.getAmount());
        dto.setCurrency(payment.getCurrency());
        dto.setReference(payment.getProviderRef());
        dto.setCheckoutUrl(payment.getCheckoutUrl());
        return dto;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void notifyPayment(Participant participant, com.racetracker.model.Notification.NotificationType type,
                               String templateCode, String title, String message) {
        if (participant == null || participant.getUser() == null) {
            return;
        }
        try {
            notificationService.notifyTemplated(participant.getUser().getId(), type, templateCode, title, message,
                java.util.Map.of("participantId", participant.getId().toString()));
        } catch (RuntimeException ex) {
            log.warn("Failed to notify participant {} about payment: {}", participant.getId(), ex.getMessage());
        }
    }

    private String raceName(Participant participant) {
        if (participant == null || participant.getRace() == null || participant.getRace().getName() == null) {
            return "your race";
        }
        return participant.getRace().getName();
    }
}
