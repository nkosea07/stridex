package com.racetracker.repository;

import com.racetracker.model.PaymentReversalCase;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PaymentReversalCaseRepository extends JpaRepository<PaymentReversalCase, UUID> {
    long countByResolvedAtIsNull();

    List<PaymentReversalCase> findByResolvedAtIsNullOrderByObservedAtAsc(Pageable pageable);
}
