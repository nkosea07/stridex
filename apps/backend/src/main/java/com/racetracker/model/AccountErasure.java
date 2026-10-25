package com.racetracker.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "account_erasures")
@Getter
@Setter
public class AccountErasure {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "staged_at", nullable = false)
    private OffsetDateTime stagedAt;

    @Column(name = "finalized_at")
    private OffsetDateTime finalizedAt;

    @Column(name = "notification_email")
    private String notificationEmail;

    @Column(name = "notification_first_name")
    private String notificationFirstName;

    @Column(name = "notification_lease_token")
    private UUID notificationLeaseToken;

    @Column(name = "notification_lease_until")
    private OffsetDateTime notificationLeaseUntil;

    @Column(name = "notification_dispatched_at")
    private OffsetDateTime notificationDispatchedAt;
}
