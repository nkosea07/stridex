package com.racetracker.service;

import com.racetracker.model.Checkpoint;
import com.racetracker.model.Race;
import com.racetracker.model.RaceStage;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Decides when an in-progress race may be closed automatically. The close instant is the latest
 * scheduled start (the race start, or the last stage's start for multi-stage races) plus the
 * course time limit plus a grace period, evaluated in the race timezone. The time limit is the
 * longest checkpoint cutoff (normally the FINISH cutoff); without any cutoff the configured
 * default applies. When the deciding start time is unknown, the anchor is local midnight and the
 * limit is at least a full day, so such a race is never closed before its day has elapsed.
 */
public final class RaceClosePolicy {

    private static final Duration FULL_DAY = Duration.ofHours(24);

    private RaceClosePolicy() {
    }

    public static OffsetDateTime closeAt(Race race, List<RaceStage> stages, List<Checkpoint> checkpoints,
                                         Duration defaultTimeLimit, Duration grace) {
        if (race == null || race.getRaceDate() == null) {
            return null;
        }
        ZoneId zone = CheckpointCutoff.zoneOf(race);
        OffsetDateTime anchor = localStart(race.getRaceDate(), race.getStartTime(), zone);
        boolean anchorTimeKnown = race.getStartTime() != null;

        if (stages != null) {
            for (RaceStage stage : stages) {
                if (stage.getStageDate() == null) {
                    continue;
                }
                LocalTime stageTime = stage.getStartTime() != null ? stage.getStartTime() : race.getStartTime();
                OffsetDateTime stageStart = localStart(stage.getStageDate(), stageTime, zone);
                if (stageStart.isAfter(anchor)) {
                    anchor = stageStart;
                    anchorTimeKnown = stageTime != null;
                }
            }
        }

        Duration limit = timeLimit(checkpoints, defaultTimeLimit);
        if (!anchorTimeKnown && limit.compareTo(FULL_DAY) < 0) {
            limit = FULL_DAY;
        }
        return anchor.plus(limit).plus(grace == null ? Duration.ZERO : grace);
    }

    static Duration timeLimit(List<Checkpoint> checkpoints, Duration defaultTimeLimit) {
        Duration longest = null;
        if (checkpoints != null) {
            for (Checkpoint checkpoint : checkpoints) {
                Duration cutoff = checkpoint.getCutoffTime();
                if (cutoff != null && !cutoff.isNegative() && (longest == null || cutoff.compareTo(longest) > 0)) {
                    longest = cutoff;
                }
            }
        }
        if (longest != null) {
            return longest;
        }
        return defaultTimeLimit == null ? Duration.ofHours(24) : defaultTimeLimit;
    }

    private static OffsetDateTime localStart(LocalDate date, LocalTime time, ZoneId zone) {
        return date.atTime(time != null ? time : LocalTime.MIDNIGHT).atZone(zone).toOffsetDateTime();
    }
}
