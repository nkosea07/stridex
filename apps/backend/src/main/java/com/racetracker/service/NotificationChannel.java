package com.racetracker.service;

import com.racetracker.model.NotificationDelivery;
import com.racetracker.notification.NotificationMessage;

/**
 * A delivery adapter for a single notification channel. Implementations must be best-effort:
 * the dispatcher records failures but must never let a channel break the caller's flow.
 */
public interface NotificationChannel {

    NotificationDelivery.Channel channel();

    /**
     * Whether the channel is configured/available for delivery. Disabled channels are
     * recorded as SKIPPED rather than attempted.
     */
    boolean isEnabled();

    void send(NotificationMessage message) throws Exception;
}
