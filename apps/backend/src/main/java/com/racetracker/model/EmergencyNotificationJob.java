package com.racetracker.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "emergency_notification_jobs")
@Getter
@Setter
public class EmergencyNotificationJob {
    public enum Status { PENDING, PROCESSING, DISPATCHED, EXHAUSTED }

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alert_id", nullable = false)
    private EmergencyAlert alert;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NotificationDelivery.Channel channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private OffsetDateTime nextAttemptAt;

    @Column(name = "lease_token")
    private UUID leaseToken;

    @Column(name = "lease_until")
    private OffsetDateTime leaseUntil;

    @Column(name = "dispatched_at")
    private OffsetDateTime dispatchedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
