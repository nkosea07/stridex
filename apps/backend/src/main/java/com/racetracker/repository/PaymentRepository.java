package com.racetracker.repository;

import com.racetracker.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByProviderAndProviderRef(String provider, String providerRef);

    Optional<Payment> findFirstByParticipantIdAndStatusOrderByCreatedAtDesc(UUID participantId, Payment.PaymentStatus status);

    Optional<Payment> findFirstByParticipantIdOrderByCreatedAtDesc(UUID participantId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") UUID id);
}
