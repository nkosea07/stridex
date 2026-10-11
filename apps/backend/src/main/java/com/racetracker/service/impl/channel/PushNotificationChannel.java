package com.racetracker.service.impl.channel;

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
        return properties.getPush() != null && properties.getPush().isEnabled();
    }

    @Override
    public void send(NotificationMessage message) {
        List<String> tokens = pushDeviceRepository.findByUserId(message.userId()).stream()
            .map(PushDevice::getToken)
            .toList();
        if (tokens.isEmpty()) {
            return;
        }
        pushNotificationService.sendToTokens(tokens, message.title(), message.body(), message.data());
    }
}
