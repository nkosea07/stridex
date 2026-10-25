package com.racetracker.repository;

import com.racetracker.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByProviderAndProviderRef(String provider, String providerRef);

    Optional<Payment> findFirstByParticipantIdAndStatusOrderByCreatedAtDesc(UUID participantId, Payment.PaymentStatus status);

    List<Payment> findByParticipantIdAndStatusOrderByCreatedAtDesc(UUID participantId, Payment.PaymentStatus status);

    @Query(value = """
        SELECT DISTINCT p.participant_id FROM payments p
        WHERE p.race_id = :raceId AND p.status = 'COMPLETED'
          AND NOT EXISTS (SELECT 1 FROM payment_refunds r WHERE r.payment_id = p.id AND r.status = 'REFUNDED')
    """, nativeQuery = true)
    List<UUID> findRefundEligibleParticipantIds(@Param("raceId") UUID raceId);

    Optional<Payment> findFirstByParticipantIdOrderByCreatedAtDesc(UUID participantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") UUID id);
}
