package com.racetracker.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "checkpoint_record_corrections")
@Getter
@Setter
@NoArgsConstructor
public class CheckpointRecordCorrection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "checkpoint_record_id", nullable = false, updatable = false)
    private UUID checkpointRecordId;

    @Column(name = "race_id", nullable = false, updatable = false)
    private UUID raceId;

    @Column(name = "participant_id", nullable = false, updatable = false)
    private UUID participantId;

    @Column(name = "checkpoint_id", nullable = false, updatable = false)
    private UUID checkpointId;

    @Column(name = "previous_effective_at", updatable = false)
    private OffsetDateTime previousEffectiveAt;

    @Column(name = "new_adjusted_at", updatable = false)
    private OffsetDateTime newAdjustedAt;

    @Column(name = "previous_verified", updatable = false)
    private Boolean previousVerified;

    @Column(name = "new_verified", updatable = false)
    private Boolean newVerified;

    @Column(nullable = false, updatable = false, length = 500)
    private String reason;

    @Column(nullable = false, updatable = false, length = 20)
    private String action = "CORRECTED";

    @Column(name = "corrected_by", updatable = false)
    private UUID correctedBy;

    @Column(name = "corrected_at", nullable = false, updatable = false)
    private OffsetDateTime correctedAt;
}
