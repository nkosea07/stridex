package com.racetracker.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.racetracker.config.RaceTrackerProperties;
import com.racetracker.exception.ApiException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Thin Pesepay payments-engine client. Pesepay encrypts every API payload with
 * AES-256-CBC using the application encryption key and the first 16 characters
 * of that key as the IV (see the Pesepay encryption guide).
 */
@Component
public class PesepayClient {

    private final RaceTrackerProperties properties;
    private final ObjectMapper objectMapper;

    public PesepayClient(RaceTrackerProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public InitiateResult initiate(double amount, String currency, String reason,
                                   String resultUrl, String returnUrl, String merchantReference) {
        RaceTrackerProperties.Payments payments = properties.getPayments();
        validateConfigured(payments);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("amountDetails", Map.of("amount", amount, "currencyCode", currency));
        body.put("reasonForPayment", reason);
        body.put("resultUrl", resultUrl);
        body.put("returnUrl", returnUrl);
        if (merchantReference != null) {
            body.put("merchantReference", merchantReference);
        }

        JsonNode response = post(payments, "/payments/initiate", body);
        JsonNode payload = decryptPayload(payments, response);

        return new InitiateResult(
            text(payload, "referenceNumber"),
            text(payload, "redirectUrl"),
            text(payload, "pollUrl")
        );
    }

    public String checkStatus(String referenceNumber) {        RaceTrackerProperties.Payments payments = properties.getPayments();
        validateConfigured(payments);

        RestClient client = RestClient.builder().baseUrl(payments.getBaseUrl()).build();
        try {
            String raw = client.get()
                .uri(uriBuilder -> uriBuilder.path("/payments/check-payment")
                    .queryParam("referenceNumber", referenceNumber).build())
                .header("authorization", payments.getIntegrationKey())
                .retrieve()
                .body(String.class);
            JsonNode payload = decryptPayload(payments, objectMapper.readTree(raw));
            return text(payload, "transactionStatus");
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException("Pesepay status check failed: " + ex.getMessage());
        }
    }

    private JsonNode post(RaceTrackerProperties.Payments payments, String path, Map<String, Object> body) {
        try {
            String encrypted = encrypt(objectMapper.writeValueAsString(body), payments.getEncryptionKey());
            String requestBody = objectMapper.writeValueAsString(Map.of("payload", encrypted));
            RestClient client = RestClient.builder().baseUrl(payments.getBaseUrl()).build();
            String raw = client.post()
                .uri(path)
                .header("authorization", payments.getIntegrationKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(String.class);
            return objectMapper.readTree(raw);
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException("Pesepay request failed: " + ex.getMessage());
        }
    }

    private JsonNode decryptPayload(RaceTrackerProperties.Payments payments, JsonNode response) {
        JsonNode payloadNode = response.get("payload");
        if (payloadNode == null || payloadNode.isNull()) {
            throw new ApiException("Pesepay response did not contain a payload");
        }
        try {
            String json = decrypt(payloadNode.asText(), payments.getEncryptionKey());
            return objectMapper.readTree(json);
        } catch (Exception ex) {
            throw new ApiException("Failed to decrypt Pesepay response: " + ex.getMessage());
        }
    }

    private void validateConfigured(RaceTrackerProperties.Payments payments) {
        if (!payments.isEnabled()) {
            throw new ApiException("Online payments are not enabled");
        }
        if (isBlank(payments.getIntegrationKey()) || isBlank(payments.getEncryptionKey())) {
            throw new ApiException("Pesepay credentials are not configured");
        }
    }

    String encrypt(String json, String encryptionKey) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        SecretKeySpec key = new SecretKeySpec(encryptionKey.getBytes(StandardCharsets.UTF_8), "AES");
        IvParameterSpec iv = new IvParameterSpec(encryptionKey.substring(0, 16).getBytes(StandardCharsets.UTF_8));
        cipher.init(Cipher.ENCRYPT_MODE, key, iv);
        return Base64.getEncoder().encodeToString(cipher.doFinal(json.getBytes(StandardCharsets.UTF_8)));
    }

    String decrypt(String payload, String encryptionKey) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        SecretKeySpec key = new SecretKeySpec(encryptionKey.getBytes(StandardCharsets.UTF_8), "AES");
        IvParameterSpec iv = new IvParameterSpec(encryptionKey.substring(0, 16).getBytes(StandardCharsets.UTF_8));
        cipher.init(Cipher.DECRYPT_MODE, key, iv);
        return new String(cipher.doFinal(Base64.getDecoder().decode(payload)), StandardCharsets.UTF_8);
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public record InitiateResult(String referenceNumber, String redirectUrl, String pollUrl) {}
}
