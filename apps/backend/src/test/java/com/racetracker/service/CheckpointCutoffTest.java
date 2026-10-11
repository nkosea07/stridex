package com.racetracker.service;

import com.racetracker.model.Checkpoint;
import com.racetracker.model.CheckpointRecord;
import com.racetracker.model.Participant;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CheckpointCutoffTest {

    private final OffsetDateTime start = OffsetDateTime.parse("2026-01-01T08:00:00Z");

    @Test
    void isMissed_returnsFalseWhenCutoffIsNull() {
        assertThat(CheckpointCutoff.isMissed(null, start, start.plusHours(5))).isFalse();
    }

    @Test
    void isMissed_returnsFalseWhenStartMissing() {
        assertThat(CheckpointCutoff.isMissed(Duration.ofHours(1), null, start.plusHours(2))).isFalse();
    }

    @Test
    void isMissed_trueWhenEffectiveAfterCutoff() {
        assertThat(CheckpointCutoff.isMissed(Duration.ofHours(1), start, start.plusHours(1).plusSeconds(1))).isTrue();
    }

    @Test
    void isMissed_falseWhenEffectiveWithinCutoff() {
        assertThat(CheckpointCutoff.isMissed(Duration.ofHours(1), start, start.plusMinutes(59))).isFalse();
    }

    @Test
    void resolveStart_prefersCheckInInstant() {
        Participant participant = Participant.builder()
            .checkedInAt(start)
            .registeredAt(start.minusDays(30))
            .build();

        assertThat(CheckpointCutoff.resolveStart(participant, List.of())).isEqualTo(start);
    }

    @Test
    void resolveStart_usesEarliestStartCheckpointWhenNoCheckIn() {
        OffsetDateTime startRecord = start.plusMinutes(2);
        OffsetDateTime registered = start.minusDays(30);
        Participant participant = Participant.builder().registeredAt(registered).build();
        CheckpointRecord record = CheckpointRecord.builder()
            .checkpoint(Checkpoint.builder().checkpointType(Checkpoint.CheckpointType.START).build())
            .recordedAt(startRecord)
            .build();

        assertThat(CheckpointCutoff.resolveStart(participant, List.of(record))).isEqualTo(startRecord);
    }

    @Test
    void resolveStart_fallsBackToRegistration() {
        OffsetDateTime registered = start.minusDays(30);
        Participant participant = Participant.builder().registeredAt(registered).build();

        assertThat(CheckpointCutoff.resolveStart(participant, List.of())).isEqualTo(registered);
    }

    @Test
    void effectiveTime_prefersAdjustedOverride() {
        OffsetDateTime adjusted = start.plusMinutes(5);
        CheckpointRecord record = CheckpointRecord.builder()
            .recordedAt(start)
            .adjustedRecordedAt(adjusted)
            .build();

        assertThat(CheckpointCutoff.effectiveTime(record)).isEqualTo(adjusted);
    }
}
