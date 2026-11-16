package com.racetracker.service;

import com.racetracker.model.Checkpoint;
import com.racetracker.model.CheckpointRecord;
import com.racetracker.model.GpsLocation;
import com.racetracker.model.Participant;
import com.racetracker.model.Race;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Race progress from every timing source. Most athletes are timed by RFID mats and never run
 * the app, so progress is the furthest of the GPS distance and the furthest timed checkpoint;
 * a finish record means the course is complete.
 */
public final class RaceProgress {

    public record Snapshot(
        BigDecimal distanceCoveredKm,
        OffsetDateTime at,
        BigDecimal distanceRemainingKm,
        Duration elapsed,
        String averagePace,
        OffsetDateTime predictedFinish,
        boolean finished
    ) {
    }

    private RaceProgress() {
    }

    public static Snapshot compute(Participant participant, GpsLocation latestFix, List<CheckpointRecord> records) {
        Race race = participant.getRace();
        OffsetDateTime start = CheckpointCutoff.resolveStart(participant, records);

        BigDecimal distance = BigDecimal.ZERO;
        OffsetDateTime at = latestFix != null ? latestFix.getRecordedAt() : null;
        if (participant.getTotalDistance() != null && participant.getTotalDistance().signum() > 0) {
            distance = participant.getTotalDistance();
        }

        boolean finished = false;
        for (CheckpointRecord record : records == null ? List.<CheckpointRecord>of() : records) {
            Checkpoint checkpoint = record.getCheckpoint();
            OffsetDateTime effective = CheckpointCutoff.effectiveTime(record);
            if (checkpoint == null || effective == null) {
                continue;
            }
            if (CheckpointPasses.isFinish(record)) {
                finished = true;
                BigDecimal course = race != null && race.getDistanceKm() != null ? race.getDistanceKm()
                    : checkpoint.getDistanceFromStartKm();
                distance = course != null ? course : distance;
                at = effective;
                break;
            }
            BigDecimal checkpointDistance = checkpoint.getDistanceFromStartKm();
            if (checkpointDistance == null) {
                continue;
            }
            int comparison = checkpointDistance.compareTo(distance);
            if (comparison > 0 || (comparison == 0 && (at == null || effective.isAfter(at)))) {
                distance = checkpointDistance;
                at = effective;
            }
        }

        Duration elapsed = start != null && at != null && at.isAfter(start) ? Duration.between(start, at) : null;
        BigDecimal remaining = null;
        if (race != null && race.getDistanceKm() != null) {
            remaining = finished ? BigDecimal.ZERO : race.getDistanceKm().subtract(distance).max(BigDecimal.ZERO);
        }

        String pace = null;
        OffsetDateTime predicted = finished ? at : null;
        if (elapsed != null && distance.signum() > 0) {
            double paceMinutes = (elapsed.toSeconds() / 60.0) / distance.doubleValue();
            int minutes = (int) paceMinutes;
            int seconds = (int) Math.round((paceMinutes - minutes) * 60);
            if (seconds == 60) {
                minutes += 1;
                seconds = 0;
            }
            pace = String.format("%d:%02d", minutes, seconds);
            if (!finished && remaining != null && paceMinutes > 0) {
                predicted = at.plus(Duration.ofSeconds(Math.round(remaining.doubleValue() * paceMinutes * 60)));
            }
        }
        return new Snapshot(distance, at, remaining, elapsed, pace, predicted, finished);
    }
}
