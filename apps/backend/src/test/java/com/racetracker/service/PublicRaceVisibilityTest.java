package com.racetracker.service;

import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.model.Race;
import com.racetracker.repository.RaceRepository;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicRaceVisibilityTest {

    private final RaceRepository races = mock(RaceRepository.class);
    private final PublicRaceVisibility visibility = new PublicRaceVisibility(races);
    private final UUID raceId = UUID.randomUUID();

    @Test
    void visibleRaceIsReturned() {
        Race race = visibleRace();
        when(races.findByIdForPublicVisibility(raceId)).thenReturn(Optional.of(race));

        assertThat(visibility.requireVisible(raceId)).isSameAs(race);
    }

    @Test
    void missingRaceIsHidden() {
        assertHidden();
    }

    @Test
    void disabledPublicRegistrationIsHidden() {
        Race race = visibleRace();
        race.setIsPublicRegistration(false);
        when(races.findByIdForPublicVisibility(raceId)).thenReturn(Optional.of(race));
        assertHidden();
    }

    @Test
    void nullPublicRegistrationIsHidden() {
        Race race = visibleRace();
        race.setIsPublicRegistration(null);
        when(races.findByIdForPublicVisibility(raceId)).thenReturn(Optional.of(race));
        assertHidden();
    }

    @Test
    void draftRaceIsHidden() {
        Race race = visibleRace();
        race.setStatus(Race.RaceStatus.DRAFT);
        when(races.findByIdForPublicVisibility(raceId)).thenReturn(Optional.of(race));
        assertHidden();
    }

    @Test
    void archivedRaceIsHidden() {
        Race race = visibleRace();
        race.setArchivedAt(OffsetDateTime.now());
        when(races.findByIdForPublicVisibility(raceId)).thenReturn(Optional.of(race));
        assertHidden();
    }

    private Race visibleRace() {
        Race race = new Race();
        race.setId(raceId);
        race.setIsPublicRegistration(true);
        race.setStatus(Race.RaceStatus.REGISTRATION_OPEN);
        return race;
    }

    private void assertHidden() {
        assertThatThrownBy(() -> visibility.requireVisible(raceId))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessage("Race not found");
    }
}
