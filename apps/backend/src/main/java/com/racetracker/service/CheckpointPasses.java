package com.racetracker.service;

import com.racetracker.model.Checkpoint;
import com.racetracker.model.CheckpointRecord;

/**
 * Pass rules for checkpoints crossed more than once. The final expected pass at a finish
 * checkpoint is the finish; on a shared start/finish mat the first pass is the chip start.
 */
public final class CheckpointPasses {

    private CheckpointPasses() {
    }

    public static int pass(CheckpointRecord record) {
        return record.getPassNumber() == null || record.getPassNumber() < 1 ? 1 : record.getPassNumber();
    }

    public static boolean isFinish(Checkpoint checkpoint, int passNumber) {
        return checkpoint != null && checkpoint.getCheckpointType() == Checkpoint.CheckpointType.FINISH
            && passNumber >= checkpoint.passes();
    }

    public static boolean isFinish(CheckpointRecord record) {
        return record != null && isFinish(record.getCheckpoint(), pass(record));
    }

    public static boolean isStart(CheckpointRecord record) {
        Checkpoint checkpoint = record == null ? null : record.getCheckpoint();
        if (checkpoint == null) {
            return false;
        }
        return checkpoint.getCheckpointType() == Checkpoint.CheckpointType.START
            || (checkpoint.getCheckpointType() == Checkpoint.CheckpointType.FINISH
                && Boolean.TRUE.equals(checkpoint.getFirstPassIsStart()) && pass(record) == 1);
    }
}
