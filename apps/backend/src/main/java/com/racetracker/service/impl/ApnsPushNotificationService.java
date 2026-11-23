package com.racetracker.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.racetracker.config.RaceTrackerProperties;
import com.racetracker.repository.PushDeviceRepository;
import com.racetracker.service.PushNotificationService;
import com.racetracker.service.PushPlatform;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Delivers push notifications to iOS devices through the Apple Push Notification service, using
 * token-based (.p8 key) authentication. The iOS app registers its native APNs device token, which
 * Firebase Cloud Messaging cannot deliver to.
 */
@Slf4j
@Service
public class ApnsPushNotificationService implements PushNotificationService {

    static final String PRODUCTION_HOST = "https://api.push.apple.com";
    static final String SANDBOX_HOST = "https://api.sandbox.push.apple.com";
    static final Duration PROVIDER_TOKEN_LIFETIME = Duration.ofMinutes(50);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final Pattern DEVICE_TOKEN = Pattern.compile("^[0-9a-fA-F]{32,256}$");
    private static final Pattern PEM_MARKER = Pattern.compile("-----(BEGIN|END) [A-Z ]+-----");
    private static final Set<String> STALE_TOKEN_REASONS = Set.of("BadDeviceToken", "Unregistered", "DeviceTokenNotForTopic");

    private final RaceTrackerProperties properties;
    private final PushDeviceRepository pushDeviceRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Clock clock;
    private PrivateKey signingKey;
    private String providerToken;
    private Instant providerTokenIssuedAt;

    @Autowired
    public ApnsPushNotificationService(RaceTrackerProperties properties, PushDeviceRepository pushDeviceRepository,
                                       ObjectMapper objectMapper) {
        this(properties, pushDeviceRepository, objectMapper,
            HttpClient.newBuilder().version(HttpClient.Version.HTTP_2).connectTimeout(REQUEST_TIMEOUT).build(),
            Clock.systemUTC());
    }

    ApnsPushNotificationService(RaceTrackerProperties properties, PushDeviceRepository pushDeviceRepository,
                                ObjectMapper objectMapper, HttpClient httpClient, Clock clock) {
        this.properties = properties;
        this.pushDeviceRepository = pushDeviceRepository;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
        this.clock = clock;
    }

    @PostConstruct
    public void initialize() {
        RaceTrackerProperties.Push push = properties.getPush();
        if (push == null || !push.isEnabled()) {
            return;
        }
        RaceTrackerProperties.Apns apns = push.getApns();
        if (apns == null || !apns.isConfigured()) {
            log.warn("Push notifications enabled but APNs is not configured; iOS push will be skipped");
            return;
        }
        try {
            this.signingKey = parsePrivateKey(apns.getPrivateKey());
            log.info("Apple Push Notification service initialized ({})", apns.isSandbox() ? "sandbox" : "production");
        } catch (Exception ex) {
            log.error("Failed to load the APNs signing key ({}); iOS push will be skipped", ex.getClass().getSimpleName());
        }
    }

    @Override
    public boolean isAvailable() {
        return signingKey != null;
    }

    @Override
    public boolean supports(String platform) {
        return PushPlatform.isIos(platform);
    }

    @Override
    public int sendToTokens(List<String> tokens, String title, String body, Map<String, String> data) {
        if (signingKey == null || tokens == null || tokens.isEmpty()) {
            return 0;
        }
        RaceTrackerProperties.Apns apns = properties.getPush().getApns();
        String payload = payload(title, body, data);
        String host = apns.isSandbox() ? SANDBOX_HOST : PRODUCTION_HOST;
        int accepted = 0;
        IllegalStateException transportFailure = null;
        for (String token : tokens) {
            if (token == null || !DEVICE_TOKEN.matcher(token).matches()) {
                prune(token);
                continue;
            }
            HttpRequest request = HttpRequest.newBuilder(URI.create(host + "/3/device/" + token))
                .timeout(REQUEST_TIMEOUT)
                .header("authorization", "bearer " + providerToken(apns))
                .header("apns-topic", apns.getBundleId())
                .header("apns-push-type", "alert")
                .header("apns-priority", "10")
                .header("content-type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .build();
            HttpResponse<String> response;
            try {
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            } catch (IOException ex) {
                transportFailure = new IllegalStateException("Push provider request failed", ex);
                continue;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Push provider request interrupted", ex);
            }
            if (response.statusCode() == 200) {
                accepted++;
                continue;
            }
            String reason = reason(response.body());
            if (response.statusCode() == 410 || STALE_TOKEN_REASONS.contains(reason)) {
                prune(token);
            } else if ("ExpiredProviderToken".equals(reason)) {
                resetProviderToken();
                log.warn("APNs reported an expired provider token; it will be regenerated");
            } else if ("InvalidProviderToken".equals(reason)) {
                log.error("APNs rejected the provider token; check APNS_KEY_ID, APNS_TEAM_ID and APNS_PRIVATE_KEY");
            } else {
                log.warn("APNs rejected a notification: status={} reason={}", response.statusCode(), reason);
            }
        }
        if (accepted == 0 && transportFailure != null) {
            throw transportFailure;
        }
        return accepted;
    }

    private synchronized String providerToken(RaceTrackerProperties.Apns apns) {
        Instant now = clock.instant();
        if (providerToken == null || providerTokenIssuedAt == null
            || !now.isBefore(providerTokenIssuedAt.plus(PROVIDER_TOKEN_LIFETIME))) {
            providerToken = Jwts.builder()
                .header().keyId(apns.getKeyId()).and()
                .issuer(apns.getTeamId())
                .issuedAt(Date.from(now))
                .signWith(signingKey, Jwts.SIG.ES256)
                .compact();
            providerTokenIssuedAt = now;
        }
        return providerToken;
    }

    private synchronized void resetProviderToken() {
        providerToken = null;
        providerTokenIssuedAt = null;
    }

    private String payload(String title, String body, Map<String, String> data) {
        Map<String, Object> alert = new LinkedHashMap<>();
        alert.put("title", title == null ? "" : title);
        alert.put("body", body == null ? "" : body);
        Map<String, Object> aps = new LinkedHashMap<>();
        aps.put("alert", alert);
        aps.put("sound", "default");
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("aps", aps);
        if (data != null && !data.isEmpty()) {
            root.put("body", data);
        }
        try {
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not serialize the push payload", ex);
        }
    }

    private String reason(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "";
        }
        try {
            JsonNode node = objectMapper.readTree(responseBody);
            return node.path("reason").asText("");
        } catch (JsonProcessingException ex) {
            return "";
        }
    }

    private void prune(String token) {
        if (token == null) {
            return;
        }
        try {
            pushDeviceRepository.deleteByToken(token);
            log.info("Removed stale iOS push device token");
        } catch (RuntimeException ex) {
            log.warn("Failed to prune stale push token: {}", ex.getMessage());
        }
    }

    static PrivateKey parsePrivateKey(String pem) throws Exception {
        String base64 = PEM_MARKER.matcher(pem.replace("\\n", "\n")).replaceAll("").replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(base64);
        return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der));
    }
}
