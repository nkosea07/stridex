package com.racetracker.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.racetracker.config.RaceTrackerProperties;
import com.racetracker.exception.ApiException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class WhatsAppCloudClient {

    private final RaceTrackerProperties properties;
    private final ObjectMapper objectMapper;

    public WhatsAppCloudClient(RaceTrackerProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public String sendTemplate(String phoneNumber, String templateName, String languageCode,
                               List<String> parameters) {
        RaceTrackerProperties.Notifications notifications = properties.getNotifications();
        if (notifications == null || !notifications.isWhatsappEnabled()) {
            throw new ApiException("WhatsApp notifications are disabled");
        }
        if (isBlank(notifications.getWhatsappPhoneNumberId()) || isBlank(notifications.getWhatsappAccessToken())) {
            throw new ApiException("WhatsApp credentials are not configured");
        }
        if (phoneNumber == null || phoneNumber.isBlank()) {
            throw new ApiException("WhatsApp recipient phone number is missing");
        }

        Map<String, Object> template = new LinkedHashMap<>();
        template.put("name", templateName);
        template.put("language", Map.of("code", languageCode));
        if (parameters != null && !parameters.isEmpty()) {
            List<Map<String, String>> values = parameters.stream()
                .map(value -> Map.of("type", "text", "text", value == null ? "" : value))
                .toList();
            template.put("components", List.of(Map.of("type", "body", "parameters", values)));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("messaging_product", "whatsapp");
        body.put("to", phoneNumber);
        body.put("type", "template");
        body.put("template", template);

        try {
            String raw = RestClient.builder()
                .baseUrl("https://graph.facebook.com")
                .build()
                .post()
                .uri("/" + notifications.getWhatsappApiVersion() + "/"
                    + notifications.getWhatsappPhoneNumberId() + "/messages")
                .header("Authorization", "Bearer " + notifications.getWhatsappAccessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(objectMapper.writeValueAsString(body))
                .retrieve()
                .body(String.class);
            JsonNode response = objectMapper.readTree(raw == null ? "{}" : raw);
            JsonNode messages = response.get("messages");
            if (messages == null || !messages.isArray() || messages.isEmpty()
                || messages.get(0).get("id") == null) {
                throw new ApiException("WhatsApp response did not include a message id");
            }
            return messages.get(0).get("id").asText();
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException("WhatsApp send failed: " + ex.getMessage());
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
