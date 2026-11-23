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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
        when(push.supports(any())).thenReturn(true);
        channel = new PushNotificationChannel(devices, List.of(push), properties);
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

    @Test
    void routesIosDevicesToApnsAndOtherDevicesToFcm() {
        PushNotificationService fcm = provider(false);
        PushNotificationService apns = provider(true);
        when(devices.findByUserId(userId)).thenReturn(List.of(
            device("fcm-token", "android"), device("apns-token", "ios"), device("legacy-token", null)));
        when(fcm.sendToTokens(anyList(), any(), any(), any())).thenReturn(2);
        when(apns.sendToTokens(anyList(), any(), any(), any())).thenReturn(1);

        routingChannel(fcm, apns).send(message);

        verify(fcm).sendToTokens(List.of("fcm-token", "legacy-token"), "SOS", "Runner needs help", Map.of());
        verify(apns).sendToTokens(List.of("apns-token"), "SOS", "Runner needs help", Map.of());
    }

    @Test
    void devicesWithoutAConfiguredProviderAreUnreachable() {
        PushNotificationService fcm = provider(false);
        PushNotificationService apns = provider(true);
        when(apns.isAvailable()).thenReturn(false);
        when(devices.findByUserId(userId)).thenReturn(List.of(device("apns-token", "ios")));

        assertThatThrownBy(() -> routingChannel(fcm, apns).send(message))
            .isInstanceOf(RecipientUnreachableException.class);
        verify(apns, never()).sendToTokens(anyList(), any(), any(), any());
        verify(fcm, never()).sendToTokens(anyList(), any(), any(), any());
    }

    @Test
    void oneProviderFailingDoesNotFailADeliveryAnotherProviderAccepted() {
        PushNotificationService fcm = provider(false);
        PushNotificationService apns = provider(true);
        when(devices.findByUserId(userId)).thenReturn(List.of(device("fcm-token", "android"), device("apns-token", "ios")));
        when(fcm.sendToTokens(anyList(), any(), any(), any())).thenThrow(new IllegalStateException("Push provider request failed"));
        when(apns.sendToTokens(anyList(), any(), any(), any())).thenReturn(1);

        assertThatCode(() -> routingChannel(fcm, apns).send(message)).doesNotThrowAnyException();
    }

    @Test
    void providerFailureIsRethrownWhenNothingWasAccepted() {
        PushNotificationService fcm = provider(false);
        PushNotificationService apns = provider(true);
        when(devices.findByUserId(userId)).thenReturn(List.of(device("fcm-token", "android"), device("apns-token", "ios")));
        when(fcm.sendToTokens(anyList(), any(), any(), any())).thenThrow(new IllegalStateException("Push provider request failed"));
        when(apns.sendToTokens(anyList(), any(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> routingChannel(fcm, apns).send(message))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Push provider request failed");
    }

    private PushNotificationChannel routingChannel(PushNotificationService fcm, PushNotificationService apns) {
        RaceTrackerProperties properties = new RaceTrackerProperties();
        properties.getPush().setEnabled(true);
        return new PushNotificationChannel(devices, List.of(fcm, apns), properties);
    }

    private PushNotificationService provider(boolean ios) {
        PushNotificationService provider = mock(PushNotificationService.class);
        when(provider.isAvailable()).thenReturn(true);
        when(provider.supports(any())).thenAnswer(invocation -> PushPlatform.isIos(invocation.getArgument(0)) == ios);
        return provider;
    }

    private PushDevice device(String token) {
        return device(token, "android");
    }

    private PushDevice device(String token, String platform) {
        PushDevice device = new PushDevice();
        device.setToken(token);
        device.setPlatform(platform);
        return device;
    }
}
