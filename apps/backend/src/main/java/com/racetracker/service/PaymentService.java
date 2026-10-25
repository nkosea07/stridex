package com.racetracker.service;

import com.racetracker.dto.participant.PaymentDto;

import java.math.BigDecimal;
import java.util.UUID;

public interface PaymentService {

    PaymentDto startCheckout(UUID raceId, UUID participantId, String promoCode, String actorEmail, boolean athleteSelfService);

    void handlePesepayCallback(String authorizationHeader, String rawBody);

    PaymentDto refund(UUID raceId, UUID participantId, BigDecimal amount, String reason, String actorEmail);

    PaymentDto stageExternalRefund(UUID raceId, UUID participantId, String reason, String actorEmail);

    PaymentDto reconcileExternalRefund(UUID raceId, UUID participantId, String providerReference,
                                       String reason, boolean refunded, String actorEmail);
}
