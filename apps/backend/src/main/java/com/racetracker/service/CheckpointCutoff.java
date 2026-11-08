package com.racetracker.service;

import com.racetracker.model.Checkpoint;
import com.racetracker.model.CheckpointRecord;
import com.racetracker.model.Participant;
import com.racetracker.model.Race;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Resolves a participant's timing start and evaluates checkpoint cutoffs against it. The start
 * is the earliest START checkpoint record (chip start), else the race's scheduled gun start
 * (race date + start time in the race timezone). Registration and desk check-in instants are
 * never timing starts. Results, live tracking, and cutoffs all share this definition.
 */
public final class CheckpointCutoff {

    private CheckpointCutoff() {
    }

    public static OffsetDateTime effectiveTime(CheckpointRecord record) {
        return record.getAdjustedRecordedAt() != null ? record.getAdjustedRecordedAt() : record.getRecordedAt();
    }

    public static OffsetDateTime resolveStart(Participant participant, List<CheckpointRecord> records) {
        OffsetDateTime chipStart = earliestStartRecord(records);
        if (chipStart != null) {
            return chipStart;
        }
        return participant == null ? null : scheduledStart(participant.getRace());
    }

    public static OffsetDateTime scheduledStart(Race race) {
        if (race == null || race.getRaceDate() == null || race.getStartTime() == null) {
            return null;
        }
        return race.getRaceDate().atTime(race.getStartTime()).atZone(zoneOf(race)).toOffsetDateTime();
    }

    public static ZoneId zoneOf(Race race) {
        String timezone = race == null ? null : race.getTimezone();
        if (timezone == null || timezone.isBlank()) {
            return ZoneOffset.UTC;
        }
        try {
            return ZoneId.of(timezone.trim());
        } catch (DateTimeException ex) {
            return ZoneOffset.UTC;
        }
    }

    public static boolean isMissed(Duration cutoff, OffsetDateTime start, OffsetDateTime effective) {
        if (cutoff == null || start == null || effective == null) {
            return false;
        }
        return Duration.between(start, effective).compareTo(cutoff) > 0;
    }

    private static OffsetDateTime earliestStartRecord(List<CheckpointRecord> records) {
        if (records == null) {
            return null;
        }
        OffsetDateTime earliest = null;
        for (CheckpointRecord record : records) {
            if (record.getCheckpoint() == null
                || record.getCheckpoint().getCheckpointType() != Checkpoint.CheckpointType.START) {
                continue;
            }
            OffsetDateTime effective = effectiveTime(record);
            if (effective != null && (earliest == null || effective.isBefore(earliest))) {
                earliest = effective;
            }
        }
        return earliest;
    }
}
