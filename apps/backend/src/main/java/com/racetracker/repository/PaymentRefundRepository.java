package com.racetracker.repository;

import com.racetracker.model.PaymentRefund;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRefundRepository extends JpaRepository<PaymentRefund, UUID> {

    List<PaymentRefund> findByPaymentId(UUID paymentId);

    Optional<PaymentRefund> findFirstByPaymentIdAndStatus(UUID paymentId, PaymentRefund.RefundStatus status);
}
