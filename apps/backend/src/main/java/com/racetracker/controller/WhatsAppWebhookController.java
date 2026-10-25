package com.racetracker.controller;

import com.racetracker.config.RaceTrackerProperties;
import com.racetracker.service.WhatsAppWebhookService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

@RestController
@RequestMapping("/v${racetracker.api.version}/webhooks/whatsapp")
public class WhatsAppWebhookController {

    private final RaceTrackerProperties properties;
    private final WhatsAppWebhookService webhookService;

    public WhatsAppWebhookController(RaceTrackerProperties properties, WhatsAppWebhookService webhookService) {
        this.properties = properties;
        this.webhookService = webhookService;
    }

    @GetMapping
    public ResponseEntity<String> verify(@RequestParam(name = "hub.mode", required = false) String mode,
                                         @RequestParam(name = "hub.verify_token", required = false) String token,
                                         @RequestParam(name = "hub.challenge", required = false) String challenge) {
        RaceTrackerProperties.Notifications notifications = properties.getNotifications();
        if (notifications != null && "subscribe".equals(mode)
            && notifications.getWhatsappVerifyToken() != null
            && MessageDigest.isEqual(notifications.getWhatsappVerifyToken().getBytes(StandardCharsets.UTF_8),
                token == null ? new byte[0] : token.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.ok(challenge == null ? "" : challenge);
        }
        return ResponseEntity.status(403).build();
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Void> receive(@RequestBody String rawBody,
                                        @RequestHeader(name = "X-Hub-Signature-256", required = false) String signature) {
        RaceTrackerProperties.Notifications notifications = properties.getNotifications();
        if (notifications == null || notifications.getWhatsappAppSecret() == null
            || notifications.getWhatsappAppSecret().isBlank()) {
            return ResponseEntity.status(503).build();
        }
        if (!validSignature(rawBody, signature, notifications.getWhatsappAppSecret())) {
            return ResponseEntity.status(403).build();
        }
        webhookService.updateDeliveryStatuses(rawBody);
        return ResponseEntity.ok().build();
    }

    private boolean validSignature(String body, String signature, String secret) {
        if (signature == null || !signature.startsWith("sha256=")) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = "sha256=" + HexFormat.of().formatHex(
                mac.doFinal((body == null ? "" : body).getBytes(StandardCharsets.UTF_8)));
            return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            return false;
        }
    }
}
