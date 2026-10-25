package com.racetracker.service.impl.channel;

import com.racetracker.config.RaceTrackerProperties;
import com.racetracker.model.NotificationDelivery;
import com.racetracker.notification.NotificationMessage;
import com.racetracker.service.NotificationChannel;
import com.racetracker.service.impl.WhatsAppCloudClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class WhatsAppNotificationChannel implements NotificationChannel {

    private final RaceTrackerProperties properties;
    private final WhatsAppCloudClient client;
    private final ThreadLocal<String> lastProviderMessageId = new ThreadLocal<>();

    public WhatsAppNotificationChannel(RaceTrackerProperties properties, WhatsAppCloudClient client) {
        this.properties = properties;
        this.client = client;
    }

    @Override
    public NotificationDelivery.Channel channel() {
        return NotificationDelivery.Channel.WHATSAPP;
    }

    @Override
    public boolean isEnabled() {
        return properties.getNotifications() != null && properties.getNotifications().isWhatsappEnabled();
    }

    @Override
    public void send(NotificationMessage message) {
        RaceTrackerProperties.Notifications notifications = properties.getNotifications();
        Map<String, String> data = message.data() == null ? Map.of() : message.data();
        String template = valueOr(data.get("whatsappTemplate"), notifications.getWhatsappDefaultTemplate());
        String language = valueOr(data.get("whatsappLanguage"), notifications.getWhatsappLanguageCode());
        lastProviderMessageId.set(client.sendTemplate(message.recipientPhone(), template, language,
            List.of(valueOr(message.title(), ""), valueOr(message.body(), ""))));
    }

    @Override
    public String lastProviderMessageId() {
        return lastProviderMessageId.get();
    }

    @Override
    public void clearProviderMessageId() {
        lastProviderMessageId.remove();
    }

    private String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
