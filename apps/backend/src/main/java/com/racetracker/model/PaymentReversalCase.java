package com.racetracker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "payment_reversal_cases")
@Getter
@Setter
public class PaymentReversalCase {
    @Id
    @Column(name = "payment_id")
    private UUID paymentId;

    @OneToOne
    @JoinColumn(name = "payment_id", insertable = false, updatable = false)
    private Payment payment;

    @Column(name = "provider_reference", nullable = false, length = 160)
    private String providerReference;

    @Column(name = "observed_at", nullable = false)
    private OffsetDateTime observedAt;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @Column(name = "resolution_reference", length = 160)
    private String resolutionReference;
}
