package com.racetracker.service;

import com.racetracker.config.RaceTrackerProperties;
import com.racetracker.service.impl.FcmPushNotificationService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;

class FcmPushNotificationServiceTest {

    @Test
    void disabledPushDoesNotThrow() {
        RaceTrackerProperties properties = new RaceTrackerProperties();
        properties.getPush().setEnabled(false);
        FcmPushNotificationService service = new FcmPushNotificationService(properties);
        service.initialize();

        assertThatCode(() -> service.sendToTokens(List.of("token-a", "token-b"), "Title", "Body", Map.of("k", "v")))
            .doesNotThrowAnyException();
    }

    @Test
    void enabledWithoutServiceAccountDoesNotThrow() {
        RaceTrackerProperties properties = new RaceTrackerProperties();
        properties.getPush().setEnabled(true);
        FcmPushNotificationService service = new FcmPushNotificationService(properties);
        service.initialize();

        assertThatCode(() -> service.sendToTokens(List.of("token-a"), "Title", "Body", Map.of()))
            .doesNotThrowAnyException();
    }
}
