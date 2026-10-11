package com.racetracker.service;

import com.racetracker.dto.notification.NotificationDto;
import com.racetracker.dto.notification.NotificationPreferenceDto;
import com.racetracker.model.Notification;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface NotificationService {
    List<NotificationDto> getMyNotifications();
    NotificationDto markRead(UUID id);
    NotificationDto notify(UUID userId, Notification.NotificationType type, String title, String message,
                           Map<String, String> data);
    void registerDevice(String token, String platform);
    void unregisterDevice(String token);
    NotificationPreferenceDto getPreferences();
    NotificationPreferenceDto updatePreferences(NotificationPreferenceDto preferences);
}
