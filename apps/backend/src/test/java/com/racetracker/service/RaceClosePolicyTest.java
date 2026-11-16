package com.racetracker.service;

import com.racetracker.model.Checkpoint;
import com.racetracker.model.Race;
import com.racetracker.model.RaceStage;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RaceClosePolicyTest {

    private static final Duration DEFAULT_LIMIT = Duration.ofHours(24);
    private static final Duration GRACE = Duration.ofHours(1);

    @Test
    void raceRunningPastMidnightClosesAfterItsCutoffInRaceTimezone() {
        Race race = race("Africa/Harare", LocalDate.of(2026, 3, 7), LocalTime.of(18, 0));

        OffsetDateTime closeAt = RaceClosePolicy.closeAt(race, List.of(), List.of(cutoff(Duration.ofHours(10))),
            DEFAULT_LIMIT, GRACE);

        assertThat(closeAt).isEqualTo(OffsetDateTime.parse("2026-03-08T05:00:00+02:00"));
    }

    @Test
    void longestCheckpointCutoffIsTheCourseTimeLimit() {
        assertThat(RaceClosePolicy.timeLimit(List.of(cutoff(Duration.ofHours(2)), cutoff(Duration.ofHours(7)), cutoff(null)),
            DEFAULT_LIMIT)).isEqualTo(Duration.ofHours(7));
        assertThat(RaceClosePolicy.timeLimit(List.of(cutoff(null)), Duration.ofHours(30))).isEqualTo(Duration.ofHours(30));
    }

    @Test
    void multiStageRaceIsAnchoredOnItsLastStage() {
        Race race = race("UTC", LocalDate.of(2026, 3, 7), LocalTime.of(7, 0));
        RaceStage day1 = stage(LocalDate.of(2026, 3, 7), null);
        RaceStage day3 = stage(LocalDate.of(2026, 3, 9), LocalTime.of(6, 30));

        OffsetDateTime closeAt = RaceClosePolicy.closeAt(race, List.of(day1, day3), List.of(cutoff(Duration.ofHours(8))),
            DEFAULT_LIMIT, GRACE);

        assertThat(closeAt).isEqualTo(OffsetDateTime.parse("2026-03-09T15:30:00Z"));
    }

    @Test
    void unknownStartTimeNeverClosesBeforeTheRaceDayEnds() {
        Race race = race("UTC", LocalDate.of(2026, 3, 7), null);

        OffsetDateTime closeAt = RaceClosePolicy.closeAt(race, List.of(), List.of(cutoff(Duration.ofHours(3))),
            DEFAULT_LIMIT, GRACE);

        assertThat(closeAt).isEqualTo(OffsetDateTime.parse("2026-03-08T01:00:00Z"));
    }

    @Test
    void raceWithoutDateIsNeverAutoClosed() {
        assertThat(RaceClosePolicy.closeAt(new Race(), List.of(), List.of(), DEFAULT_LIMIT, GRACE)).isNull();
    }

    private Race race(String timezone, LocalDate date, LocalTime start) {
        Race race = new Race();
        race.setTimezone(timezone);
        race.setRaceDate(date);
        race.setStartTime(start);
        return race;
    }

    private RaceStage stage(LocalDate date, LocalTime start) {
        RaceStage stage = new RaceStage();
        stage.setStageDate(date);
        stage.setStartTime(start);
        return stage;
    }

    private Checkpoint cutoff(Duration duration) {
        Checkpoint checkpoint = new Checkpoint();
        checkpoint.setCutoffTime(duration);
        return checkpoint;
    }
}
