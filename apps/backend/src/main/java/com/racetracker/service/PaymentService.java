package com.racetracker.service;

import com.racetracker.dto.participant.PaymentDto;
import com.racetracker.dto.participant.ExternalRefundAttemptDto;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.List;

public interface PaymentService {

    PaymentDto startCheckout(UUID raceId, UUID participantId, String promoCode, String actorEmail, boolean athleteSelfService);

    void handlePesepayCallback(String authorizationHeader, String rawBody);

    PaymentDto refund(UUID raceId, UUID participantId, BigDecimal amount, String reason, String actorEmail);

    PaymentDto stageExternalRefund(UUID raceId, UUID participantId, String reason, String actorEmail);

    PaymentDto reconcileExternalRefund(UUID raceId, UUID participantId, String providerReference,
                                       String reason, boolean refunded, String actorEmail);

    ExternalRefundAttemptDto stageExternalRefundAttempt(UUID raceId, UUID participantId, UUID paymentId,
                                                        String reason, String actorEmail);

    ExternalRefundAttemptDto reconcileExternalRefundAttempt(UUID raceId, UUID participantId, UUID attemptId,
                                                            String providerReference, String reason,
                                                            boolean refunded, String actorEmail);

    ExternalRefundAttemptDto getExternalRefundAttempt(UUID raceId, UUID participantId, UUID attemptId);

    List<ExternalRefundAttemptDto> listExternalRefundAttempts(UUID raceId, UUID participantId);

    List<PaymentDto> eligibleRefundPayments(UUID raceId, UUID participantId);

    List<UUID> eligibleRefundParticipantIds(UUID raceId);
}
