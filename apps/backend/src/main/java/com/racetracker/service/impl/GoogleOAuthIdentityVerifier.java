package com.racetracker.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.racetracker.config.RaceTrackerProperties;
import com.racetracker.exception.ApiException;
import com.racetracker.service.OAuthIdentity;
import com.racetracker.service.OAuthIdentityVerifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Locale;

/**
 * Verifies Google ID tokens using Google's tokeninfo endpoint. No Google SDK dependency is
 * required; when a client id is configured the token audience is checked against it.
 */
@Component
public class GoogleOAuthIdentityVerifier implements OAuthIdentityVerifier {

    private static final String TOKENINFO_BASE = "https://oauth2.googleapis.com";

    private final RaceTrackerProperties properties;
    private final ObjectMapper objectMapper;

    public GoogleOAuthIdentityVerifier(RaceTrackerProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Override
    public OAuthIdentity verifyGoogle(String idToken) {
        if (idToken == null || idToken.isBlank()) {
            throw new ApiException("A Google ID token is required");
        }
        try {
            RestClient client = RestClient.create(TOKENINFO_BASE);
            String raw = client.get()
                .uri(uriBuilder -> uriBuilder.path("/tokeninfo").queryParam("id_token", idToken).build())
                .retrieve()
                .body(String.class);
            JsonNode node = objectMapper.readTree(raw == null ? "{}" : raw);

            String clientId = properties.getOauth() == null ? null : properties.getOauth().getGoogleClientId();
            if (clientId != null && !clientId.isBlank()) {
                String audience = text(node, "aud");
                if (!clientId.equals(audience)) {
                    throw new ApiException("Google token audience does not match the configured client id");
                }
            }

            String email = text(node, "email");
            if (email == null || email.isBlank()) {
                throw new ApiException("Google token did not include an email");
            }
            boolean emailVerified = "true".equalsIgnoreCase(text(node, "email_verified"))
                || node.path("email_verified").asBoolean(false);
            String firstName = text(node, "given_name");
            String lastName = text(node, "family_name");

            return new OAuthIdentity("google", text(node, "sub"), email.trim().toLowerCase(Locale.ROOT),
                emailVerified,
                firstName == null ? "Athlete" : firstName,
                lastName == null ? "" : lastName);
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException("Failed to verify Google identity token");
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
