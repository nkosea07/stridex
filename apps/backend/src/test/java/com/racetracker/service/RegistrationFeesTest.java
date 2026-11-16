package com.racetracker.service;

import com.racetracker.model.Participant;
import com.racetracker.model.Race;
import com.racetracker.model.RaceCategory;
import com.racetracker.model.Team;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class RegistrationFeesTest {

    private Race race() {
        Race race = new Race();
        race.setRegistrationFee(BigDecimal.valueOf(40));
        race.setEarlyBirdFee(BigDecimal.valueOf(30));
        race.setTeamFee(BigDecimal.valueOf(120));
        return race;
    }

    @Test
    void categoryFeeOverridesTheRaceFee() {
        Race race = race();
        RaceCategory category = new RaceCategory();
        category.setRegistrationFee(BigDecimal.valueOf(25));
        Participant participant = new Participant();
        participant.setCategory(category);

        assertThat(RegistrationFees.current(participant, race)).isEqualByComparingTo("25");
    }

    @Test
    void earlyBirdAppliesOnlyUntilItEnds() {
        Race race = race();
        Participant participant = new Participant();
        race.setEarlyBirdEndsAt(OffsetDateTime.now().plusDays(1));
        assertThat(RegistrationFees.current(participant, race)).isEqualByComparingTo("30");
        race.setEarlyBirdEndsAt(OffsetDateTime.now().minusDays(1));
        assertThat(RegistrationFees.current(participant, race)).isEqualByComparingTo("40");
    }

    @Test
    void teamEntriesPayTheTeamFee() {
        Participant participant = new Participant();
        participant.setTeam(new Team());

        assertThat(RegistrationFees.current(participant, race())).isEqualByComparingTo("120");
    }
}
