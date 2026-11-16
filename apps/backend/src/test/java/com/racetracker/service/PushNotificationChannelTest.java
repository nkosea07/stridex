package com.racetracker.service;

import com.racetracker.config.RaceTrackerProperties;
import com.racetracker.model.Notification;
import com.racetracker.model.NotificationDelivery;
import com.racetracker.model.PushDevice;
import com.racetracker.notification.NotificationMessage;
import com.racetracker.notification.RecipientUnreachableException;
import com.racetracker.repository.PushDeviceRepository;
import com.racetracker.service.impl.NotificationDeliveryRecorder;
import com.racetracker.service.impl.NotificationDispatcher;
import com.racetracker.service.impl.channel.PushNotificationChannel;
import com.racetracker.websocket.TrackingLivePublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PushNotificationChannelTest {

    private final UUID userId = UUID.randomUUID();
    private final NotificationMessage message = new NotificationMessage(
        userId, null, null, Notification.NotificationType.ALERT, "SOS", "Runner needs help", Map.of());
    private final PushDeviceRepository devices = mock(PushDeviceRepository.class);
    private final PushNotificationService push = mock(PushNotificationService.class);
    private PushNotificationChannel channel;

    @BeforeEach
    void setUp() {
        RaceTrackerProperties properties = new RaceTrackerProperties();
        properties.getPush().setEnabled(true);
        when(push.isAvailable()).thenReturn(true);
        channel = new PushNotificationChannel(devices, push, properties);
    }

    @Test
    void recipientWithoutDevicesIsUnreachableNotSent() {
        when(devices.findByUserId(userId)).thenReturn(List.of());

        assertThatThrownBy(() -> channel.send(message)).isInstanceOf(RecipientUnreachableException.class);
    }

    @Test
    void providerAcceptingNoTokenIsAFailure() {
        when(devices.findByUserId(userId)).thenReturn(List.of(device("token-a")));
        when(push.sendToTokens(anyList(), any(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> channel.send(message))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("accepted none");
    }

    @Test
    void channelIsDisabledWhenTheProviderIsNotInitialized() {
        when(push.isAvailable()).thenReturn(false);

        assertThat(channel.isEnabled()).isFalse();
    }

    @Test
    void dispatcherRecordsUnreachableRecipientsAsSkipped() {
        when(devices.findByUserId(userId)).thenReturn(List.of());
        NotificationDeliveryRecorder recorder = mock(NotificationDeliveryRecorder.class);
        NotificationPreferenceService preferences = mock(NotificationPreferenceService.class);
        when(preferences.isChannelEnabled(any(), any())).thenReturn(true);
        UUID notificationId = UUID.randomUUID();

        new NotificationDispatcher(List.of(channel), recorder, mock(TrackingLivePublisher.class), preferences)
            .dispatch(notificationId, message);

        verify(recorder).record(eq(notificationId), eq(NotificationDelivery.Channel.PUSH),
            eq(NotificationDelivery.Status.SKIPPED), eq("no registered push devices"));
    }

    private PushDevice device(String token) {
        PushDevice device = new PushDevice();
        device.setToken(token);
        return device;
    }
}
