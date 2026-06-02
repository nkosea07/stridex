package com.racetracker.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcType;
import org.hibernate.dialect.PostgreSQLEnumJdbcType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "rfid_read_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RfidReadEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reader_id")
    private RfidReader reader;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "race_id", nullable = false)
    private Race race;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checkpoint_id", nullable = false)
    private Checkpoint checkpoint;

    @Column(name = "rfid_tag", nullable = false)
    private String rfidTag;

    @Column(name = "source_event_id")
    private String sourceEventId;

    @Column(name = "read_at", nullable = false)
    private OffsetDateTime readAt;

    @CreationTimestamp
    @Column(name = "received_at")
    private OffsetDateTime receivedAt;

    private String antenna;

    @Column(precision = 8, scale = 2)
    private BigDecimal rssi;

    @Column(name = "raw_payload", columnDefinition = "TEXT")
    private String rawPayload;

    @Enumerated(EnumType.STRING)
    @JdbcType(PostgreSQLEnumJdbcType.class)
    @Column(nullable = false, columnDefinition = "rfid_read_event_status")
    private RfidReadEventStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "matched_participant_id")
    private Participant matchedParticipant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checkpoint_record_id")
    private CheckpointRecord checkpointRecord;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    public enum RfidReadEventStatus {
        ACCEPTED, DUPLICATE, UNKNOWN_TAG, INVALID_READER, ERROR
    }
}
