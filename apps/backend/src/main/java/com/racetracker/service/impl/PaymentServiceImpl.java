package com.racetracker.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.racetracker.config.RaceTrackerProperties;
import com.racetracker.dto.participant.PaymentDto;
import com.racetracker.exception.ApiException;
import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.model.Participant;
import com.racetracker.model.Payment;
import com.racetracker.model.Race;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.PaymentRepository;
import com.racetracker.service.PaymentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public PaymentServiceImpl(
        PaymentRepository paymentRepository,
        ParticipantRepository participantRepository,
        PesepayClient pesepayClient,
        RaceTrackerProperties properties,
        ObjectMapper objectMapper
    ) {
        this.paymentRepository = paymentRepository;
        this.participantRepository = participantRepository;
        this.pesepayClient = pesepayClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public PaymentDto startCheckout(UUID raceId, UUID participantId) {
        RaceTrackerProperties.Payments payments = properties.getPayments();
        if (isBlank(payments.getResultUrl()) || isBlank(payments.getReturnUrl())) {
            throw new ApiException("Payment result/return URLs are not configured");
        }

        Participant participant = participantRepository.findByRaceIdAndId(raceId, participantId)
            .orElseThrow(() -> new ResourceNotFoundException("Participant not found"));
        Race race = participant.getRace();

        BigDecimal amount = participant.getCategory() != null && participant.getCategory().getRegistrationFee() != null
            ? participant.getCategory().getRegistrationFee()
            : race.getRegistrationFee();
        if (amount == null || amount.signum() <= 0) {
            throw new ApiException("This race does not require an online payment");
        }
        String currency = !isBlank(race.getCurrency()) ? race.getCurrency() : payments.getCurrency();

        String merchantReference = "RACE-" + raceId + "-P-" + participantId + "-" + UUID.randomUUID().toString().substring(0, 8);

        Payment payment = Payment.builder()
            .race(race)
            .participant(participant)
            .provider(PROVIDER)
            .merchantReference(merchantReference)
            .amount(amount)
            .currency(currency)
            .status(Payment.PaymentStatus.PENDING)
            .build();
        payment = paymentRepository.save(payment);

        PesepayClient.InitiateResult result = pesepayClient.initiate(
            amount.doubleValue(),
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
        if (payment.getStatus() == Payment.PaymentStatus.COMPLETED) {
            return;
        }

        String status = pesepayClient.checkStatus(referenceNumber);
        applyStatus(payment, status);
    }

    private void applyStatus(Payment payment, String transactionStatus) {
        String normalized = transactionStatus == null ? "" : transactionStatus.trim().toUpperCase();
        switch (normalized) {
            case "SUCCESS" -> {
                payment.setStatus(Payment.PaymentStatus.COMPLETED);
                payment.setCompletedAt(OffsetDateTime.now());
                paymentRepository.save(payment);

                Participant participant = payment.getParticipant();
                participant.setPaymentStatus(Participant.PaymentStatus.PAID);
                participant.setPaymentReference(payment.getProviderRef());
                participant.setPaymentAmount(payment.getAmount());
                participant.setPaymentVerifiedAt(OffsetDateTime.now());
                participantRepository.save(participant);
            }
            case "FAILED" -> {
                payment.setStatus(Payment.PaymentStatus.FAILED);
                paymentRepository.save(payment);
            }
            case "CANCELLED" -> {
                payment.setStatus(Payment.PaymentStatus.CANCELLED);
                paymentRepository.save(payment);
            }
            case "REVERSED" -> {
                payment.setStatus(Payment.PaymentStatus.REFUNDED);
                paymentRepository.save(payment);
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
}
