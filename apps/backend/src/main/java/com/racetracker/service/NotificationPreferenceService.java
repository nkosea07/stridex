package com.racetracker.service;

import com.racetracker.dto.notification.NotificationPreferenceDto;
import com.racetracker.model.NotificationDelivery;

import java.util.UUID;

public interface NotificationPreferenceService {

    NotificationPreferenceDto get(UUID userId);

    NotificationPreferenceDto update(UUID userId, NotificationPreferenceDto preferences);

    boolean isChannelEnabled(UUID userId, NotificationDelivery.Channel channel);
}
