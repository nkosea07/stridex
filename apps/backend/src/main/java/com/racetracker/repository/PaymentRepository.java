package com.racetracker.repository;

import com.racetracker.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByProviderAndProviderRef(String provider, String providerRef);

    Optional<Payment> findFirstByParticipantIdAndStatusOrderByCreatedAtDesc(UUID participantId, Payment.PaymentStatus status);

    Optional<Payment> findFirstByParticipantIdOrderByCreatedAtDesc(UUID participantId);
}
