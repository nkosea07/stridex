package com.racetracker.service;

import com.racetracker.model.Notification;
import com.racetracker.model.NotificationDelivery;
import com.racetracker.notification.NotificationMessage;
import com.racetracker.service.impl.NotificationDeliveryRecorder;
import com.racetracker.service.impl.NotificationDispatcher;
import com.racetracker.websocket.TrackingLivePublisher;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationDispatcherTest {

    private final UUID notificationId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final NotificationMessage message = new NotificationMessage(
        userId, "runner@example.com", null, Notification.NotificationType.RACE_UPDATE,
        "Title", "Body", Map.of());

    @Test
    void enabledChannelsAreSentAndDisabledChannelsAreSkipped() throws Exception {
        NotificationChannel push = channel(NotificationDelivery.Channel.PUSH, true);
        NotificationChannel email = channel(NotificationDelivery.Channel.EMAIL, false);
        NotificationDeliveryRecorder recorder = mock(NotificationDeliveryRecorder.class);
        TrackingLivePublisher publisher = mock(TrackingLivePublisher.class);
        NotificationPreferenceService preferences = mock(NotificationPreferenceService.class);
        when(preferences.isChannelEnabled(any(), any())).thenReturn(true);

        NotificationDispatcher dispatcher = new NotificationDispatcher(List.of(push, email), recorder, publisher, preferences);
        dispatcher.dispatch(notificationId, message);

        verify(push).send(message);
        verify(email, never()).send(any());
        verify(recorder).record(notificationId, NotificationDelivery.Channel.PUSH, NotificationDelivery.Status.SENT, null);
        verify(recorder).record(notificationId, NotificationDelivery.Channel.EMAIL, NotificationDelivery.Status.SKIPPED, null);
        verify(publisher).publishUserAlert(eq(userId), any());
    }

    @Test
    void failingChannelIsRecordedButDoesNotBreakFanOut() throws Exception {
        NotificationChannel push = channel(NotificationDelivery.Channel.PUSH, true);
        NotificationChannel email = channel(NotificationDelivery.Channel.EMAIL, true);
        doThrow(new RuntimeException("boom")).when(push).send(message);
        NotificationDeliveryRecorder recorder = mock(NotificationDeliveryRecorder.class);
        TrackingLivePublisher publisher = mock(TrackingLivePublisher.class);
        NotificationPreferenceService preferences = mock(NotificationPreferenceService.class);
        when(preferences.isChannelEnabled(any(), any())).thenReturn(true);
        NotificationDispatcher dispatcher = new NotificationDispatcher(List.of(push, email), recorder, publisher, preferences);

        assertThatCode(() -> dispatcher.dispatch(notificationId, message)).doesNotThrowAnyException();

        verify(recorder).record(notificationId, NotificationDelivery.Channel.PUSH, NotificationDelivery.Status.FAILED, "boom");
        verify(recorder).record(notificationId, NotificationDelivery.Channel.EMAIL, NotificationDelivery.Status.SENT, null);
        verify(publisher).publishUserAlert(eq(userId), any());
    }

    @Test
    void userOptOutSkipsChannel() throws Exception {
        NotificationChannel push = channel(NotificationDelivery.Channel.PUSH, true);
        NotificationDeliveryRecorder recorder = mock(NotificationDeliveryRecorder.class);
        TrackingLivePublisher publisher = mock(TrackingLivePublisher.class);
        NotificationPreferenceService preferences = mock(NotificationPreferenceService.class);
        when(preferences.isChannelEnabled(userId, NotificationDelivery.Channel.PUSH)).thenReturn(false);
        NotificationDispatcher dispatcher = new NotificationDispatcher(List.of(push), recorder, publisher, preferences);

        dispatcher.dispatch(notificationId, message);

        verify(push, never()).send(any());
        verify(recorder).record(notificationId, NotificationDelivery.Channel.PUSH, NotificationDelivery.Status.SKIPPED, "disabled by user preference");
    }

    private NotificationChannel channel(NotificationDelivery.Channel type, boolean enabled) {
        NotificationChannel channel = mock(NotificationChannel.class);
        when(channel.channel()).thenReturn(type);
        when(channel.isEnabled()).thenReturn(enabled);
        return channel;
    }
}
