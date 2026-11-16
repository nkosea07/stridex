package com.racetracker.service;

import com.racetracker.dto.result.TimingAnomalyDto;
import com.racetracker.model.Checkpoint;
import com.racetracker.model.CheckpointRecord;
import com.racetracker.model.Participant;
import com.racetracker.model.Race;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TimingAnomalyDetectorTest {

    private final OffsetDateTime gun = OffsetDateTime.parse("2026-11-15T06:00:00Z");
    private final Race race = Race.builder().startedAt(gun).build();
    private final Participant athlete = Participant.builder().id(UUID.randomUUID()).race(race).bibNumber("101").build();

    private Checkpoint mat(String name, Checkpoint.CheckpointType type, double km) {
        return Checkpoint.builder().id(UUID.randomUUID()).name(name).checkpointType(type)
            .distanceFromStartKm(BigDecimal.valueOf(km)).hasRfidReader(true).selfScanToken("t").build();
    }

    private CheckpointRecord at(Checkpoint checkpoint, int minutes) {
        CheckpointRecord record = new CheckpointRecord();
        record.setParticipant(athlete);
        record.setCheckpoint(checkpoint);
        record.setRecordedAt(gun.plusMinutes(minutes));
        return record;
    }

    @Test
    void finisherWhoSkippedATimedMatIsFlaggedForReview() {
        Checkpoint ten = mat("10 km", Checkpoint.CheckpointType.INTERMEDIATE, 10);
        Checkpoint fifteen = mat("15 km", Checkpoint.CheckpointType.INTERMEDIATE, 15);
        Checkpoint finish = mat("Finish", Checkpoint.CheckpointType.FINISH, 21.1);

        List<TimingAnomalyDto> anomalies = TimingAnomalyDetector.detect(athlete,
            List.of(at(ten, 50), at(finish, 110)), List.of(ten, fifteen, finish), 30);

        assertThat(anomalies).extracting(TimingAnomalyDto::type, TimingAnomalyDto::checkpointName)
            .containsExactly(org.assertj.core.groups.Tuple.tuple(TimingAnomalyDetector.MISSING_SPLIT, "15 km"));
    }

    @Test
    void impossiblyFastSegmentAndOutOfOrderCrossingsAreFlagged() {
        Checkpoint ten = mat("10 km", Checkpoint.CheckpointType.INTERMEDIATE, 10);
        Checkpoint fifteen = mat("15 km", Checkpoint.CheckpointType.INTERMEDIATE, 15);
        Checkpoint twenty = mat("20 km", Checkpoint.CheckpointType.INTERMEDIATE, 20);

        List<TimingAnomalyDto> anomalies = TimingAnomalyDetector.detect(athlete,
            List.of(at(ten, 50), at(fifteen, 55), at(twenty, 54)), List.of(ten, fifteen, twenty), 30);

        assertThat(anomalies).extracting(TimingAnomalyDto::type)
            .containsExactly(TimingAnomalyDetector.IMPLAUSIBLE_SPLIT, TimingAnomalyDetector.OUT_OF_ORDER);
        assertThat(anomalies.get(0).speedKmh()).isEqualByComparingTo("60.0");
    }

    @Test
    void cleanRaceHasNoAnomalies() {
        Checkpoint ten = mat("10 km", Checkpoint.CheckpointType.INTERMEDIATE, 10);
        Checkpoint finish = mat("Finish", Checkpoint.CheckpointType.FINISH, 21.1);

        assertThat(TimingAnomalyDetector.detect(athlete, List.of(at(ten, 50), at(finish, 106)), List.of(ten, finish), 30))
            .isEmpty();
    }
}
