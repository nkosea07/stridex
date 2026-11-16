package com.racetracker.service.impl.channel;

import com.racetracker.notification.RecipientUnreachableException;

import com.racetracker.config.RaceTrackerProperties;
import com.racetracker.model.NotificationDelivery;
import com.racetracker.model.PushDevice;
import com.racetracker.notification.NotificationMessage;
import com.racetracker.repository.PushDeviceRepository;
import com.racetracker.service.NotificationChannel;
import com.racetracker.service.PushNotificationService;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PushNotificationChannel implements NotificationChannel {

    private final PushDeviceRepository pushDeviceRepository;
    private final PushNotificationService pushNotificationService;
    private final RaceTrackerProperties properties;

    public PushNotificationChannel(
        PushDeviceRepository pushDeviceRepository,
        PushNotificationService pushNotificationService,
        RaceTrackerProperties properties
    ) {
        this.pushDeviceRepository = pushDeviceRepository;
        this.pushNotificationService = pushNotificationService;
        this.properties = properties;
    }

    @Override
    public NotificationDelivery.Channel channel() {
        return NotificationDelivery.Channel.PUSH;
    }

    @Override
    public boolean isEnabled() {
        return properties.getPush() != null && properties.getPush().isEnabled() && pushNotificationService.isAvailable();
    }

    @Override
    public void send(NotificationMessage message) {
        List<String> tokens = pushDeviceRepository.findByUserId(message.userId()).stream()
            .map(PushDevice::getToken)
            .toList();
        if (tokens.isEmpty()) {
            throw new RecipientUnreachableException("no registered push devices");
        }
        int accepted = pushNotificationService.sendToTokens(tokens, message.title(), message.body(), message.data());
        if (accepted == 0) {
            throw new IllegalStateException("Push provider accepted none of " + tokens.size() + " device token(s)");
        }
    }
}
