package com.racetracker.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;
import org.hibernate.type.SqlTypes;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "checkpoint_records", 
       uniqueConstraints = @UniqueConstraint(columnNames = {"participant_id", "checkpoint_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CheckpointRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "participant_id", nullable = false)
    private Participant participant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checkpoint_id", nullable = false)
    private Checkpoint checkpoint;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "race_id", nullable = false)
    private Race race;

    @Column(name = "recorded_at", nullable = false)
    private OffsetDateTime recordedAt;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(name = "record_method", nullable = false, columnDefinition = "record_method")
    private RecordMethod recordMethod;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by")
    private User recordedBy;

    @Column(name = "split_time")
    @JdbcTypeCode(SqlTypes.INTERVAL_SECOND)
    private Duration splitTime;

    @Column(name = "elapsed_time")
    @JdbcTypeCode(SqlTypes.INTERVAL_SECOND)
    private Duration elapsedTime;

    @Column(name = "is_verified")
    private Boolean isVerified = false;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    public enum RecordMethod {
        RFID, QR_CODE, MANUAL, GPS_AUTO
    }
}
