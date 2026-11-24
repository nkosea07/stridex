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
        when(participants.existsByRaceIdAndBibNumber(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq("CT-0007"))).thenReturn(false);
        Participant participant = new Participant();
        participant.setRace(race);

        new ParticipantIdentityService(participants, races).assign(participant);

        assertThat(participant.getAthleteCode()).startsWith("ATH-");
        assertThat(participant.getBibNumber()).isEqualTo("CT-0007");
        assertThat(race.getNextBibSequence()).isEqualTo(8);
    }

    @Test
    void entriesWithoutAPlaceGetAnAthleteCodeButNoBib() {
        ParticipantRepository participants = mock(ParticipantRepository.class);
        RaceRepository races = mock(RaceRepository.class);
        Race race = new Race();
        race.setId(UUID.randomUUID());
        when(races.findByIdForUpdate(race.getId())).thenReturn(Optional.of(race));
        for (Participant.ParticipantStatus status : java.util.List.of(Participant.ParticipantStatus.WAITLISTED,
            Participant.ParticipantStatus.PENDING_REVIEW, Participant.ParticipantStatus.REJECTED,
            Participant.ParticipantStatus.WITHDRAWN)) {
            Participant participant = new Participant();
            participant.setRace(race);
            participant.setStatus(status);

            new ParticipantIdentityService(participants, races).assign(participant);

            assertThat(participant.getAthleteCode()).startsWith("ATH-");
            assertThat(participant.getBibNumber()).isNull();
        }
        org.mockito.Mockito.verify(participants, org.mockito.Mockito.never())
            .existsByRaceIdAndBibNumber(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void categoryPrefixesKeepTheirOwnSequence() {
        ParticipantRepository participants = mock(ParticipantRepository.class);
        RaceRepository races = mock(RaceRepository.class);
        Race race = new Race();
        race.setId(UUID.randomUUID());
        race.setBibPrefix("CT-");
        race.setBibWidth(4);
        race.setNextBibSequence(7);
        when(races.findByIdForUpdate(race.getId())).thenReturn(Optional.of(race));
        when(participants.findBibNumbersByRaceIdLike(race.getId(), "W\\_%"))
            .thenReturn(java.util.List.of("W_0003", "W_0010", "W_X1"));
        com.racetracker.model.RaceCategory women = com.racetracker.model.RaceCategory.builder()
            .id(UUID.randomUUID()).race(race).name("Women").bibPrefix("W_").build();
        Participant participant = new Participant();
        participant.setRace(race);
        participant.setCategory(women);
        participant.setStatus(Participant.ParticipantStatus.CONFIRMED);

        new ParticipantIdentityService(participants, races).assign(participant);

        assertThat(participant.getBibNumber()).isEqualTo("W_0011");
        assertThat(race.getNextBibSequence()).isEqualTo(7);
    }
}
