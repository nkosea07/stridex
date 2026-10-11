package com.racetracker.service;

import com.racetracker.dto.participant.PaymentDto;

import java.util.UUID;

public interface PaymentService {

    PaymentDto startCheckout(UUID raceId, UUID participantId, String promoCode);

    void handlePesepayCallback(String authorizationHeader, String rawBody);

    PaymentDto refund(UUID raceId, UUID participantId);
}
