package com.racetracker.service;

import com.racetracker.model.Notification;
import com.racetracker.model.Participant;
import com.racetracker.model.Race;
import com.racetracker.model.User;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.RaceRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResultNotifierTest {

    private final ParticipantRepository participants = mock(ParticipantRepository.class);
    private final RaceRepository races = mock(RaceRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private final ResultNotifier notifier = new ResultNotifier(participants, races, notifications);
    private final UUID raceId = UUID.randomUUID();

    @Test
    void athletesWithAccountsAreToldWhenResultsAppearAndWhenTheyAreOfficial() {
        User account = User.builder().id(UUID.randomUUID()).build();
        when(races.findById(raceId)).thenReturn(Optional.of(Race.builder().id(raceId).name("City 10K").build()));
        when(participants.findByRaceId(raceId)).thenReturn(List.of(
            Participant.builder().id(UUID.randomUUID()).user(account).build(),
            Participant.builder().id(UUID.randomUUID()).build()));

        notifier.resultsPublished(raceId, 1, false);
        notifier.resultsPublished(raceId, 3, false);
        notifier.resultsPublished(raceId, 3, true);

        verify(notifications, times(1)).notifyLocalized(eq(account.getId()), eq(Notification.NotificationType.RACE_UPDATE),
            eq("RESULTS_PROVISIONAL"), any(), any());
        verify(notifications, times(1)).notifyLocalized(eq(account.getId()), eq(Notification.NotificationType.RACE_UPDATE),
            eq("RESULTS_OFFICIAL"), any(), any());
        verify(notifications, never()).notifyLocalized(eq(null), any(), any(), any(), any());
    }
}
