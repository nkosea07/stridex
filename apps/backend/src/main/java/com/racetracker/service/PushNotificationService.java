package com.racetracker.service;

import java.util.List;
import java.util.Map;

public interface PushNotificationService {

    /**
     * Whether a push provider is initialized and able to accept messages.
     */
    boolean isAvailable();

    /**
     * Sends a push notification to the given device tokens and returns how many the provider
     * accepted. Returns 0 without throwing when push is disabled or misconfigured; provider
     * transport errors propagate so callers can record and retry them.
     */
    int sendToTokens(List<String> tokens, String title, String body, Map<String, String> data);
}
