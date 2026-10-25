package com.racetracker.service;

import com.racetracker.config.RaceTrackerProperties;
import com.racetracker.model.Notification;
import com.racetracker.model.NotificationDelivery;
import com.racetracker.notification.NotificationMessage;
import com.racetracker.service.impl.WhatsAppCloudClient;
import com.racetracker.service.impl.channel.WhatsAppNotificationChannel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WhatsAppNotificationChannelTest {

    @Test
    void sendsConfiguredTemplateAndReturnsProviderMessageId() {
        RaceTrackerProperties properties = new RaceTrackerProperties();
        properties.getNotifications().setWhatsappEnabled(true);
        properties.getNotifications().setWhatsappDefaultTemplate("race_update");
        properties.getNotifications().setWhatsappLanguageCode("en_US");
        WhatsAppCloudClient client = mock(WhatsAppCloudClient.class);
        when(client.sendTemplate("+27820000000", "race_alert", "en_US",
            List.of("Alert", "Checkpoint 10K"))).thenReturn("wamid.1");
        WhatsAppNotificationChannel channel = new WhatsAppNotificationChannel(properties, client);

        channel.clearProviderMessageId();
        channel.send(new NotificationMessage(UUID.randomUUID(), "a@example.com", "+27820000000",
            Notification.NotificationType.ALERT, "Alert", "Checkpoint 10K",
            Map.of("whatsappTemplate", "race_alert")));

        assertThat(channel.lastProviderMessageId()).isEqualTo("wamid.1");
        verify(client).sendTemplate("+27820000000", "race_alert", "en_US",
            List.of("Alert", "Checkpoint 10K"));
    }
}
