package com.racetracker.notification;

import com.racetracker.model.Notification;

import java.util.Map;
import java.util.UUID;

/**
 * Channel-agnostic notification payload. Carries the recipient routing fields the channel
 * adapters need so they do not have to reload the user.
 */
public record NotificationMessage(
    UUID userId,
    String recipientEmail,
    String recipientPhone,
    Notification.NotificationType type,
    String title,
    String body,
    Map<String, String> data
) {
}
