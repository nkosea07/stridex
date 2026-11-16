package com.racetracker.service;

import com.racetracker.model.Checkpoint;
import com.racetracker.model.CheckpointRecord;
import com.racetracker.model.GpsLocation;
import com.racetracker.model.Participant;
import com.racetracker.model.Race;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RaceProgressTest {

    private final OffsetDateTime gun = OffsetDateTime.parse("2026-11-15T06:00:00Z");
    private final Race race = Race.builder().distanceKm(BigDecimal.valueOf(21.1)).startedAt(gun).build();

    private CheckpointRecord record(Checkpoint.CheckpointType type, double km, Duration afterGun) {
        Checkpoint checkpoint = Checkpoint.builder().checkpointType(type)
            .distanceFromStartKm(BigDecimal.valueOf(km)).selfScanToken("t").build();
        CheckpointRecord record = new CheckpointRecord();
        record.setCheckpoint(checkpoint);
        record.setRecordedAt(gun.plus(afterGun));
        return record;
    }

    @Test
    void athleteTimedOnlyByRfidShowsProgressPaceAndEta() {
        Participant participant = Participant.builder().race(race).build();
        List<CheckpointRecord> records = List.of(
            record(Checkpoint.CheckpointType.START, 0, Duration.ofSeconds(30)),
            record(Checkpoint.CheckpointType.INTERMEDIATE, 10, Duration.ofMinutes(50).plusSeconds(30)));

        RaceProgress.Snapshot progress = RaceProgress.compute(participant, null, records);

        assertThat(progress.distanceRemainingKm()).isEqualByComparingTo("11.1");
        assertThat(progress.averagePace()).isEqualTo("5:00");
        assertThat(progress.predictedFinish()).isEqualTo(gun.plusMinutes(50).plusSeconds(30).plusSeconds(3330));
        assertThat(progress.finished()).isFalse();
    }

    @Test
    void finishRecordMeansNothingRemainsEvenWithoutGps() {
        Participant participant = Participant.builder().race(race).build();
        List<CheckpointRecord> records = List.of(
            record(Checkpoint.CheckpointType.START, 0, Duration.ZERO),
            record(Checkpoint.CheckpointType.FINISH, 21.1, Duration.ofMinutes(105).plusSeconds(30)));

        RaceProgress.Snapshot progress = RaceProgress.compute(participant, null, records);

        assertThat(progress.distanceRemainingKm()).isEqualByComparingTo("0");
        assertThat(progress.finished()).isTrue();
        assertThat(progress.elapsed()).isEqualTo(Duration.ofMinutes(105).plusSeconds(30));
    }

    @Test
    void gpsDistanceWinsWhenItIsFurtherThanTheLastMat() {
        Participant participant = Participant.builder().race(race).totalDistance(BigDecimal.valueOf(12.5)).build();
        GpsLocation fix = new GpsLocation();
        fix.setRecordedAt(gun.plusMinutes(62));
        List<CheckpointRecord> records = List.of(record(Checkpoint.CheckpointType.INTERMEDIATE, 10, Duration.ofMinutes(50)));

        RaceProgress.Snapshot progress = RaceProgress.compute(participant, fix, records);

        assertThat(progress.distanceRemainingKm()).isEqualByComparingTo("8.6");
        assertThat(progress.at()).isEqualTo(gun.plusMinutes(62));
    }
}
