package com.racetracker.service;

import com.racetracker.dto.result.TimingAnomalyDto;
import com.racetracker.model.Checkpoint;
import com.racetracker.model.CheckpointRecord;
import com.racetracker.model.Participant;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Flags timing that officials should review before certifying results. Nothing here changes a
 * result: a finisher missing a timed mat may have cut the course or the mat may have missed them;
 * an impossibly fast segment may be a swapped chip or a read from the wrong side of a mat.
 */
public final class TimingAnomalyDetector {

    public static final String MISSING_SPLIT = "MISSING_SPLIT";
    public static final String IMPLAUSIBLE_SPLIT = "IMPLAUSIBLE_SPLIT";
    public static final String OUT_OF_ORDER = "OUT_OF_ORDER";

    private TimingAnomalyDetector() {
    }

    public static List<TimingAnomalyDto> detect(Participant participant, List<CheckpointRecord> records,
                                                List<Checkpoint> timedMats, double maxSpeedKmh) {
        List<TimingAnomalyDto> anomalies = new ArrayList<>();
        boolean finished = records.stream().anyMatch(CheckpointPasses::isFinish);
        if (finished) {
            Set<UUID> crossed = records.stream().filter(record -> record.getCheckpoint() != null)
                .map(record -> record.getCheckpoint().getId()).collect(Collectors.toSet());
            for (Checkpoint mat : timedMats) {
                if (mat.getCheckpointType() != Checkpoint.CheckpointType.FINISH && !crossed.contains(mat.getId())) {
                    anomalies.add(anomaly(participant, MISSING_SPLIT, mat, null));
                }
            }
        }

        List<CheckpointRecord> byDistance = records.stream()
            .filter(record -> record.getCheckpoint() != null && record.getCheckpoint().getDistanceFromStartKm() != null
                && CheckpointCutoff.effectiveTime(record) != null)
            .sorted(Comparator.comparing((CheckpointRecord record) -> record.getCheckpoint().getDistanceFromStartKm()))
            .toList();
        OffsetDateTime start = CheckpointCutoff.resolveStart(participant, records);
        BigDecimal previousKm = BigDecimal.ZERO;
        OffsetDateTime previousAt = start;
        for (CheckpointRecord record : byDistance) {
            BigDecimal km = record.getCheckpoint().getDistanceFromStartKm();
            OffsetDateTime at = CheckpointCutoff.effectiveTime(record);
            if (previousAt != null && km.compareTo(previousKm) > 0) {
                long seconds = Duration.between(previousAt, at).getSeconds();
                if (seconds <= 0) {
                    anomalies.add(anomaly(participant, OUT_OF_ORDER, record.getCheckpoint(), null));
                } else {
                    double speed = km.subtract(previousKm).doubleValue() / (seconds / 3600.0);
                    if (maxSpeedKmh > 0 && speed > maxSpeedKmh) {
                        anomalies.add(anomaly(participant, IMPLAUSIBLE_SPLIT, record.getCheckpoint(),
                            BigDecimal.valueOf(speed).setScale(1, RoundingMode.HALF_UP)));
                    }
                }
            }
            if (at != null) {
                previousKm = km;
                previousAt = at;
            }
        }
        return anomalies;
    }

    private static TimingAnomalyDto anomaly(Participant participant, String type, Checkpoint checkpoint, BigDecimal speed) {
        return new TimingAnomalyDto(participant.getId(), participant.getFullName(), participant.getBibNumber(), type,
            checkpoint.getId(), checkpoint.getName(), speed);
    }
}
