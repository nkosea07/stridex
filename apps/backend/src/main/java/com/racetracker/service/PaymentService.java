package com.racetracker.service;

import com.racetracker.dto.participant.PaymentDto;

import java.util.UUID;

public interface PaymentService {

    PaymentDto startCheckout(UUID raceId, UUID participantId);

    void handlePesepayCallback(String authorizationHeader, String rawBody);
}
