package com.racetracker.service.impl.channel;

import com.racetracker.config.RaceTrackerProperties;
import com.racetracker.model.NotificationDelivery;
import com.racetracker.notification.NotificationMessage;
import com.racetracker.service.NotificationChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * SMS adapter placeholder. No provider is wired yet; enabling it without a provider logs
 * and drops the message. The channel stays behind a config flag until a provider is chosen.
 */
@Slf4j
@Component
public class SmsNotificationChannel implements NotificationChannel {

    private final RaceTrackerProperties properties;

    public SmsNotificationChannel(RaceTrackerProperties properties) {
        this.properties = properties;
    }

    @Override
    public NotificationDelivery.Channel channel() {
        return NotificationDelivery.Channel.SMS;
    }

    @Override
    public boolean isEnabled() {
        return properties.getNotifications() != null && properties.getNotifications().isSmsEnabled();
    }

    @Override
    public void send(NotificationMessage message) {
        log.info("SMS notification requested for user {} but no SMS provider is configured", message.userId());
    }
}
