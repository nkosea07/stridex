package com.racetracker.service;

import com.racetracker.model.Participant;
import com.racetracker.model.Race;
import com.racetracker.model.RaceCategory;
import com.racetracker.model.User;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.RaceRepository;
import com.racetracker.service.impl.ParticipantIdentityService;
import com.racetracker.service.impl.RegistrationCapacity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegistrationCapacityTest {

    private final ParticipantRepository participants = mock(ParticipantRepository.class);
    private final RaceRepository races = mock(RaceRepository.class);
    private final ParticipantIdentityService identity = mock(ParticipantIdentityService.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final RegistrationCapacity capacity = new RegistrationCapacity(participants, races, identity, notifications);
    private final UUID raceId = UUID.randomUUID();
    private Race race;

    @BeforeEach
    void setUp() {
        race = new Race();
        race.setId(raceId);
        race.setName("City 10K");
        race.setStatus(Race.RaceStatus.REGISTRATION_OPEN);
        race.setMaxParticipants(10);
        when(races.findByIdForUpdate(raceId)).thenReturn(Optional.of(race));
        when(participants.save(any(Participant.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void promotesWaitlistedAthletesIntoEveryFreePlaceSkippingFullCategories() {
        RaceCategory elite = RaceCategory.builder().id(UUID.randomUUID()).name("Elite").maxParticipants(2).build();
        RaceCategory open = RaceCategory.builder().id(UUID.randomUUID()).name("Open").build();
        Participant blocked = waiting(elite, 1);
        Participant first = waiting(open, 2);
        Participant second = waiting(open, 3);
        Participant third = waiting(open, 4);
        when(participants.findByRaceIdAndStatusOrderByWaitlistPositionAscRegisteredAtAsc(raceId,
            Participant.ParticipantStatus.WAITLISTED)).thenReturn(List.of(blocked, first, second, third));
        when(participants.countByRaceIdAndStatusIn(eq(raceId), any())).thenReturn(8L, 8L, 9L, 10L);
        when(participants.countByRaceIdAndCategoryIdAndStatusIn(eq(raceId), eq(elite.getId()), any())).thenReturn(2L);

        int promoted = capacity.fillFreedPlaces(raceId);

        assertThat(promoted).isEqualTo(2);
        assertThat(blocked.getStatus()).isEqualTo(Participant.ParticipantStatus.WAITLISTED);
        assertThat(first.getStatus()).isEqualTo(Participant.ParticipantStatus.REGISTERED);
        assertThat(second.getStatus()).isEqualTo(Participant.ParticipantStatus.REGISTERED);
        assertThat(third.getStatus()).isEqualTo(Participant.ParticipantStatus.WAITLISTED);
        assertThat(first.getWaitlistPosition()).isNull();
        verify(identity).assign(first);
        verify(notifications).notifyLocalized(eq(first.getUser().getId()), any(), eq("registration.place_offered"), any(), any());
    }

    @Test
    void approvalRacesPromoteIntoReview() {
        race.setRequireApproval(true);
        Participant next = waiting(null, 1);
        when(participants.findByRaceIdAndStatusOrderByWaitlistPositionAscRegisteredAtAsc(raceId,
            Participant.ParticipantStatus.WAITLISTED)).thenReturn(List.of(next));

        capacity.fillFreedPlaces(raceId);

        assertThat(next.getStatus()).isEqualTo(Participant.ParticipantStatus.PENDING_REVIEW);
    }

    @Test
    void nobodyIsPromotedOnceTheRaceHasStarted() {
        for (Race.RaceStatus status : List.of(Race.RaceStatus.IN_PROGRESS, Race.RaceStatus.COMPLETED, Race.RaceStatus.CANCELLED)) {
            race.setStatus(status);
            assertThat(capacity.fillFreedPlaces(raceId)).isZero();
        }
        verify(participants, never()).findByRaceIdAndStatusOrderByWaitlistPositionAscRegisteredAtAsc(any(), any());
    }

    private Participant waiting(RaceCategory category, int position) {
        Participant participant = new Participant();
        participant.setId(UUID.randomUUID());
        participant.setRace(race);
        participant.setCategory(category);
        participant.setUser(User.builder().id(UUID.randomUUID()).build());
        participant.setStatus(Participant.ParticipantStatus.WAITLISTED);
        participant.setWaitlistPosition(position);
        return participant;
    }
}
