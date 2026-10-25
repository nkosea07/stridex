package com.racetracker.service;

import com.racetracker.model.Participant;
import com.racetracker.model.Race;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.RaceRepository;
import com.racetracker.service.impl.ParticipantIdentityService;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ParticipantIdentityServiceTest {

    @Test
    void allocatesAthleteCodeAndRaceScopedBib() {
        ParticipantRepository participants = mock(ParticipantRepository.class);
        RaceRepository races = mock(RaceRepository.class);
        Race race = new Race();
        race.setId(UUID.randomUUID());
        race.setBibPrefix("CT-");
        race.setBibWidth(4);
        race.setNextBibSequence(7);
        when(races.findByIdForUpdate(race.getId())).thenReturn(Optional.of(race));
        when(participants.existsByAthleteCode(org.mockito.ArgumentMatchers.anyString())).thenReturn(false);
        when(participants.existsByBibNumber("CT-0007")).thenReturn(false);
        Participant participant = new Participant();
        participant.setRace(race);

        new ParticipantIdentityService(participants, races).assign(participant);

        assertThat(participant.getAthleteCode()).startsWith("ATH-");
        assertThat(participant.getBibNumber()).isEqualTo("CT-0007");
        assertThat(race.getNextBibSequence()).isEqualTo(8);
    }
}
