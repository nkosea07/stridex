package com.racetracker.repository;

import com.racetracker.model.PaymentRefund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRefundRepository extends JpaRepository<PaymentRefund, UUID> {

    List<PaymentRefund> findByPaymentId(UUID paymentId);

    Optional<PaymentRefund> findFirstByPaymentIdAndStatus(UUID paymentId, PaymentRefund.RefundStatus status);

    Optional<PaymentRefund> findByProviderRef(String providerRef);

    @Query("select refund.payment.id from PaymentRefund refund where refund.id = :attemptId")
    Optional<UUID> findPaymentIdByAttemptId(@Param("attemptId") UUID attemptId);

    List<PaymentRefund> findByPaymentParticipantIdAndStatus(UUID participantId, PaymentRefund.RefundStatus status);

    Page<PaymentRefund> findByPaymentParticipantIdOrderByCreatedAtDesc(UUID participantId, Pageable pageable);
}
