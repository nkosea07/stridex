package com.racetracker.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "emergency_alerts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmergencyAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "race_id", nullable = false)
    private Race race;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_id")
    private Participant participant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reported_by")
    private User reportedBy;

    @Column(precision = 10, scale = 8)
    private BigDecimal latitude;

    @Column(precision = 11, scale = 8)
    private BigDecimal longitude;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmergencyStatus status = EmergencyStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_status", nullable = false, length = 24)
    private DeliveryStatus deliveryStatus = DeliveryStatus.QUEUED;

    @Column(name = "acknowledged_at")
    private OffsetDateTime acknowledgedAt;

    @Column(name = "acknowledged_by")
    private UUID acknowledgedBy;

    @CreationTimestamp
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    public enum EmergencyStatus {
        ACTIVE, ACKNOWLEDGED, RESOLVED
    }

    public enum DeliveryStatus {
        QUEUED, DISPATCHED, ESCALATION_REQUIRED, LEGACY_UNVERIFIED
    }
}
