package com.racetracker.service.impl.channel;

import com.racetracker.notification.RecipientUnreachableException;

import com.racetracker.config.RaceTrackerProperties;
import com.racetracker.model.NotificationDelivery;
import com.racetracker.model.PushDevice;
import com.racetracker.notification.NotificationMessage;
import com.racetracker.repository.PushDeviceRepository;
import com.racetracker.service.NotificationChannel;
import com.racetracker.service.PushNotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class PushNotificationChannel implements NotificationChannel {

    private final PushDeviceRepository pushDeviceRepository;
    private final List<PushNotificationService> providers;
    private final RaceTrackerProperties properties;

    public PushNotificationChannel(
        PushDeviceRepository pushDeviceRepository,
        List<PushNotificationService> providers,
        RaceTrackerProperties properties
    ) {
        this.pushDeviceRepository = pushDeviceRepository;
        this.providers = List.copyOf(providers);
        this.properties = properties;
    }

    @Override
    public NotificationDelivery.Channel channel() {
        return NotificationDelivery.Channel.PUSH;
    }

    @Override
    public boolean isEnabled() {
        return properties.getPush() != null && properties.getPush().isEnabled()
            && providers.stream().anyMatch(PushNotificationService::isAvailable);
    }

    @Override
    public void send(NotificationMessage message) {
        List<PushDevice> devices = pushDeviceRepository.findByUserId(message.userId());
        if (devices.isEmpty()) {
            throw new RecipientUnreachableException("no registered push devices");
        }
        Map<PushNotificationService, List<String>> tokensByProvider = new LinkedHashMap<>();
        for (PushDevice device : devices) {
            providers.stream()
                .filter(provider -> provider.isAvailable() && provider.supports(device.getPlatform()))
                .findFirst()
                .ifPresent(provider -> tokensByProvider.computeIfAbsent(provider, key -> new ArrayList<>()).add(device.getToken()));
        }
        if (tokensByProvider.isEmpty()) {
            throw new RecipientUnreachableException("no push provider is configured for the registered devices");
        }
        int routed = 0;
        int accepted = 0;
        RuntimeException failure = null;
        for (Map.Entry<PushNotificationService, List<String>> entry : tokensByProvider.entrySet()) {
            routed += entry.getValue().size();
            try {
                accepted += entry.getKey().sendToTokens(entry.getValue(), message.title(), message.body(), message.data());
            } catch (RuntimeException ex) {
                if (failure == null) {
                    failure = ex;
                }
            }
        }
        if (accepted > 0) {
            if (failure != null) {
                log.warn("Push delivered to {} of {} device token(s); a provider failed: {}", accepted, routed, failure.getMessage());
            }
            return;
        }
        if (failure != null) {
            throw failure;
        }
        throw new IllegalStateException("Push provider accepted none of " + routed + " device token(s)");
    }
}
