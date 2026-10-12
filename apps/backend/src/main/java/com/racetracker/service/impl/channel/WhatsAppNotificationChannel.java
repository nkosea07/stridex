package com.racetracker.service.impl.channel;

import com.racetracker.config.RaceTrackerProperties;
import com.racetracker.model.NotificationDelivery;
import com.racetracker.notification.NotificationMessage;
import com.racetracker.service.NotificationChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * WhatsApp adapter placeholder. No provider is wired yet; enabling it without a provider fails
 * the delivery (recorded as FAILED) rather than reporting a false success. The channel stays
 * behind a config flag until a provider is chosen.
 */
@Slf4j
@Component
public class WhatsAppNotificationChannel implements NotificationChannel {

    private final RaceTrackerProperties properties;

    public WhatsAppNotificationChannel(RaceTrackerProperties properties) {
        this.properties = properties;
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
        throw new com.racetracker.exception.ApiException("No WhatsApp provider is configured");
    }
}
