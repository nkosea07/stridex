package com.racetracker.service.impl;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import com.racetracker.config.RaceTrackerProperties;
import com.racetracker.repository.PushDeviceRepository;
import com.racetracker.service.PushNotificationService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class FcmPushNotificationService implements PushNotificationService {

    private final RaceTrackerProperties properties;
    private final PushDeviceRepository pushDeviceRepository;
    private FirebaseMessaging messaging;

    public FcmPushNotificationService(RaceTrackerProperties properties, PushDeviceRepository pushDeviceRepository) {
        this.properties = properties;
        this.pushDeviceRepository = pushDeviceRepository;
    }

    @PostConstruct
    public void initialize() {
        RaceTrackerProperties.Push push = properties.getPush();
        if (push == null || !push.isEnabled()) {
            log.info("Push notifications disabled");
            return;
        }
        String serviceAccountJson = push.getServiceAccountJson();
        if (serviceAccountJson == null || serviceAccountJson.isBlank()) {
            log.warn("Push notifications enabled but no service account configured; push will be skipped");
            return;
        }
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                GoogleCredentials credentials = GoogleCredentials.fromStream(
                    new ByteArrayInputStream(serviceAccountJson.getBytes(StandardCharsets.UTF_8)));
                FirebaseOptions.Builder builder = FirebaseOptions.builder().setCredentials(credentials);
                if (push.getProjectId() != null && !push.getProjectId().isBlank()) {
                    builder.setProjectId(push.getProjectId());
                }
                FirebaseApp.initializeApp(builder.build());
            }
            this.messaging = FirebaseMessaging.getInstance();
            log.info("Firebase Cloud Messaging initialized");
        } catch (Exception ex) {
            log.error("Failed to initialize Firebase Cloud Messaging; push will be skipped", ex);
        }
    }

    @Override
    public void sendToTokens(List<String> tokens, String title, String body, Map<String, String> data) {
        if (messaging == null || tokens == null || tokens.isEmpty()) {
            return;
        }
        try {
            MulticastMessage message = MulticastMessage.builder()
                .addAllTokens(tokens)
                .setNotification(Notification.builder().setTitle(title).setBody(body).build())
                .putAllData(data == null ? Map.of() : data)
                .build();
            BatchResponse response = messaging.sendEachForMulticast(message);
            pruneStaleTokens(tokens, response);
        } catch (Exception ex) {
            log.warn("Failed to deliver push notification: {}", ex.getMessage());
        }
    }

    private void pruneStaleTokens(List<String> tokens, BatchResponse response) {
        if (response.getFailureCount() == 0) {
            return;
        }
        List<SendResponse> responses = response.getResponses();
        List<String> stale = new ArrayList<>();
        int index = 0;
        for (SendResponse sendResponse : responses) {
            if (!sendResponse.isSuccessful() && index < tokens.size()) {
                MessagingErrorCode code = sendResponse.getException() == null
                    ? null : sendResponse.getException().getMessagingErrorCode();
                if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT) {
                    stale.add(tokens.get(index));
                }
            }
            index++;
        }
        for (String token : stale) {
            try {
                pushDeviceRepository.deleteByToken(token);
                log.info("Removed stale push device token");
            } catch (RuntimeException ex) {
                log.warn("Failed to prune stale push token: {}", ex.getMessage());
            }
        }
    }
}
