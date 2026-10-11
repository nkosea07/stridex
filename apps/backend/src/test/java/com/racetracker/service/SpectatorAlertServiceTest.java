package com.racetracker.service;

import com.racetracker.model.AthleteFollow;
import com.racetracker.model.Checkpoint;
import com.racetracker.model.Notification;
import com.racetracker.model.Participant;
import com.racetracker.model.Race;
import com.racetracker.model.User;
import com.racetracker.repository.AthleteFollowRepository;
import com.racetracker.repository.NotificationRepository;
import com.racetracker.service.impl.SpectatorAlertServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpectatorAlertServiceTest {

    @Test
    void checkpointAlertNotifiesFollowersThroughDispatcher() {
        AthleteFollowRepository followRepository = mock(AthleteFollowRepository.class);
        NotificationRepository notificationRepository = mock(NotificationRepository.class);
        NotificationService notificationService = mock(NotificationService.class);
        SpectatorAlertServiceImpl service =
            new SpectatorAlertServiceImpl(followRepository, notificationRepository, notificationService);

        Race race = new Race();
        race.setId(UUID.randomUUID());
        User follower = User.builder().id(UUID.randomUUID()).build();
        Participant participant = new Participant();
        participant.setId(UUID.randomUUID());
        participant.setRace(race);
        participant.setFirstName("Ada");
        participant.setLastName("Runner");
        participant.setBibNumber("101");
        AthleteFollow follow = AthleteFollow.builder().id(UUID.randomUUID()).user(follower).race(race).participant(participant).build();

        Checkpoint checkpoint = new Checkpoint();
        checkpoint.setId(UUID.randomUUID());
        checkpoint.setName("Water Point");

        when(followRepository.findByRaceIdAndParticipantId(race.getId(), participant.getId())).thenReturn(List.of(follow));
        when(notificationRepository.existsByUserIdAndTitle(eq(follower.getId()), any())).thenReturn(false);

        service.onCheckpoint(participant, checkpoint);

        verify(notificationService).notify(eq(follower.getId()),
            eq(Notification.NotificationType.CHECKPOINT_PASSED), any(), any(), any());
    }

    @Test
    void existingAlertIsDeduplicated() {
        AthleteFollowRepository followRepository = mock(AthleteFollowRepository.class);
        NotificationRepository notificationRepository = mock(NotificationRepository.class);
        NotificationService notificationService = mock(NotificationService.class);
        SpectatorAlertServiceImpl service =
            new SpectatorAlertServiceImpl(followRepository, notificationRepository, notificationService);

        Race race = new Race();
        race.setId(UUID.randomUUID());
        User follower = User.builder().id(UUID.randomUUID()).build();
        Participant participant = new Participant();
        participant.setId(UUID.randomUUID());
        participant.setRace(race);
        participant.setFirstName("Ada");
        participant.setLastName("Runner");
        AthleteFollow follow = AthleteFollow.builder().id(UUID.randomUUID()).user(follower).race(race).participant(participant).build();

        when(followRepository.findByRaceIdAndParticipantId(race.getId(), participant.getId())).thenReturn(List.of(follow));
        when(notificationRepository.existsByUserIdAndTitle(eq(follower.getId()), any())).thenReturn(true);

        service.onStarted(participant);

        verify(notificationService, never()).notify(any(), any(), any(), any(), any());
    }

    @Test
    void noFollowersIsNoOp() {
        AthleteFollowRepository followRepository = mock(AthleteFollowRepository.class);
        NotificationRepository notificationRepository = mock(NotificationRepository.class);
        NotificationService notificationService = mock(NotificationService.class);
        SpectatorAlertServiceImpl service =
            new SpectatorAlertServiceImpl(followRepository, notificationRepository, notificationService);

        Race race = new Race();
        race.setId(UUID.randomUUID());
        Participant participant = new Participant();
        participant.setId(UUID.randomUUID());
        participant.setRace(race);

        when(followRepository.findByRaceIdAndParticipantId(race.getId(), participant.getId())).thenReturn(List.of());

        service.onFinished(participant);

        verify(notificationService, never()).notify(any(), any(), any(), any(), any());
    }
}
