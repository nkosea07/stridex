package com.racetracker.service;

import com.racetracker.dto.participant.PaymentDto;

import java.math.BigDecimal;
import java.util.UUID;

public interface PaymentService {

    PaymentDto startCheckout(UUID raceId, UUID participantId, String promoCode);

    void handlePesepayCallback(String authorizationHeader, String rawBody);

    PaymentDto refund(UUID raceId, UUID participantId, BigDecimal amount, String reason, String actorEmail);
}
