package com.racetracker.service;

import com.racetracker.model.Checkpoint;
import com.racetracker.model.CheckpointRecord;
import com.racetracker.model.Participant;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Evaluates a checkpoint cutoff against a participant's effective start. The start is the
 * check-in instant, else the earliest START checkpoint, else the registration instant --
 * matching how results are computed.
 */
public final class CheckpointCutoff {

    private CheckpointCutoff() {
    }

    public static OffsetDateTime effectiveTime(CheckpointRecord record) {
        return record.getAdjustedRecordedAt() != null ? record.getAdjustedRecordedAt() : record.getRecordedAt();
    }

    public static OffsetDateTime resolveStart(Participant participant, List<CheckpointRecord> records) {
        if (participant.getCheckedInAt() != null) {
            return participant.getCheckedInAt();
        }
        if (records != null) {
            for (CheckpointRecord record : records) {
                if (record.getCheckpoint() != null
                    && record.getCheckpoint().getCheckpointType() == Checkpoint.CheckpointType.START) {
                    return effectiveTime(record);
                }
            }
        }
        return participant.getRegisteredAt();
    }

    public static boolean isMissed(Duration cutoff, OffsetDateTime start, OffsetDateTime effective) {
        if (cutoff == null || start == null || effective == null) {
            return false;
        }
        return Duration.between(start, effective).compareTo(cutoff) > 0;
    }
}
