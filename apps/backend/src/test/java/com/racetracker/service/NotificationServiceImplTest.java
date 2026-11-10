package com.racetracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.racetracker.model.Notification;
import com.racetracker.model.User;
import com.racetracker.repository.NotificationRepository;
import com.racetracker.repository.PushDeviceRepository;
import com.racetracker.repository.UserRepository;
import com.racetracker.service.impl.NotificationDispatcher;
import com.racetracker.service.impl.NotificationServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationServiceImplTest {

    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final NotificationDispatcher dispatcher = mock(NotificationDispatcher.class);
    private final NotificationServiceImpl service = new NotificationServiceImpl(notifications, users,
        mock(PushDeviceRepository.class), dispatcher, new ObjectMapper(),
        mock(NotificationPreferenceService.class), mock(NotificationTemplateService.class));
    private final UUID followerId = UUID.randomUUID();

    private void follower(User user) {
        when(users.findById(followerId)).thenReturn(Optional.of(user));
        when(notifications.save(any(Notification.class))).thenAnswer(inv -> {
            Notification saved = inv.getArgument(0);
            saved.setId(UUID.randomUUID());
            return saved;
        });
    }

    @Test
    void notificationsAboutAnAthleteAreLinkedToThatParticipant() {
        follower(User.builder().id(followerId).email("fan@example.com").build());
        UUID athleteParticipant = UUID.randomUUID();

        service.notify(followerId, Notification.NotificationType.RACE_UPDATE, "Ada finished", "Bib 7 finished",
            Map.of("participantId", athleteParticipant.toString()));

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(saved.capture());
        assertThat(saved.getValue().getSubjectParticipantId()).isEqualTo(athleteParticipant);
    }

    @Test
    void malformedOrMissingParticipantIdLeavesTheNotificationUnlinked() {
        follower(User.builder().id(followerId).email("fan@example.com").build());

        service.notify(followerId, Notification.NotificationType.ALERT, "Hello", "Body", Map.of("participantId", "not-a-uuid"));

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notifications).save(saved.capture());
        assertThat(saved.getValue().getSubjectParticipantId()).isNull();
    }

    @Test
    void erasedUsersAreNotNotified() {
        when(users.findById(followerId)).thenReturn(Optional.of(User.builder().id(followerId)
            .deletedAt(java.time.OffsetDateTime.now()).build()));

        assertThat(service.notify(followerId, Notification.NotificationType.ALERT, "Hi", "Body", Map.of())).isNull();
        verify(notifications, never()).save(any());
    }
}
