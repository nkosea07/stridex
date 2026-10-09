package com.racetracker.service;

import java.util.List;
import java.util.Map;

public interface PushNotificationService {

    /**
     * Best-effort delivery of a push notification to the given device tokens.
     * Implementations must not throw when push is disabled or misconfigured.
     */
    void sendToTokens(List<String> tokens, String title, String body, Map<String, String> data);
}
