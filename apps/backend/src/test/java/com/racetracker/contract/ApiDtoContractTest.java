package com.racetracker.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.racetracker.dto.emergency.EmergencyAlertDto;
import com.racetracker.dto.emergency.EmergencyAlertRequest;
import com.racetracker.dto.emergency.EmergencyAlertStatusRequest;
import com.racetracker.dto.media.MediaConsentDto;
import com.racetracker.dto.media.MediaConsentRequest;
import com.racetracker.dto.media.MediaDto;
import com.racetracker.dto.participant.ExternalRefundAttemptDto;
import com.racetracker.dto.participant.ExternalRefundAttemptRequest;
import com.racetracker.dto.participant.ExternalRefundReconciliationRequest;
import com.racetracker.dto.participant.ReversalCaseDto;
import com.racetracker.dto.privacy.PrivacyDeletionJobDto;
import com.racetracker.dto.privacy.PrivacyScopeVerificationRequest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ApiDtoContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void responseDtosSerializeWithTheClientFieldNames() throws Exception {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        assertFields(objectMapper.valueToTree(new PrivacyDeletionJobDto(id, id, "key", "local|/tmp", "PENDING",
            1, now, now)), Set.of("id", "userId", "storageKey", "storageScope", "status", "attempts",
            "nextAttemptAt", "createdAt"));
        assertFields(objectMapper.valueToTree(new ReversalCaseDto(id, id, "Race", id, "Runner", "PESEPAY",
            "PROV-1", BigDecimal.TEN, "USD", "COMPLETED", now, id)), Set.of("paymentId", "raceId", "raceName",
            "participantId", "participantName", "provider", "providerReference", "amount", "currency",
            "paymentStatus", "observedAt", "pendingRefundAttemptId"));
        assertFields(objectMapper.valueToTree(new ExternalRefundAttemptDto(id, id, BigDecimal.TEN, "USD", "PENDING",
            "PAY-1", "PROV-1", now)), Set.of("id", "paymentId", "amount", "currency", "status",
            "paymentReference", "providerReference", "createdAt"));
        MediaDto media = new MediaDto();
        media.setId(id);
        media.setRaceId(id);
        media.setParticipantId(id);
        media.setMediaType("IMAGE");
        media.setUrl("/content");
        media.setStatus("APPROVED");
        media.setParticipantConsent(true);
        media.setNonPersonAttested(false);
        media.setPubliclyVisible(true);
        media.setCreatedAt(now);
        assertFields(objectMapper.valueToTree(media), Set.of("id", "raceId", "participantId", "mediaType", "url",
            "thumbnailUrl", "width", "height", "caption", "status", "participantConsent", "nonPersonAttested",
            "publiclyVisible", "createdAt"));
        EmergencyAlertDto alert = new EmergencyAlertDto();
        alert.setId(id);
        alert.setRaceId(id);
        alert.setStatus("ACTIVE");
        alert.setDeliveryStatus("QUEUED");
        alert.setCreatedAt(now);
        assertFields(objectMapper.valueToTree(alert), Set.of("id", "raceId", "participantId", "participantName",
            "latitude", "longitude", "message", "status", "deliveryStatus", "createdAt"));
        assertFields(objectMapper.valueToTree(new MediaConsentDto(id, true, "MEDIA_PUBLICATION_V1", now)),
            Set.of("participantId", "consented", "version", "acceptedAt"));
    }

    @Test
    void requestDtosDeserializeTheClientPayloads() throws Exception {
        assertThat(objectMapper.readValue("{\"reason\":\"Duplicate\",\"paymentId\":\"" + UUID.randomUUID()
            + "\"}", ExternalRefundAttemptRequest.class).reason()).isEqualTo("Duplicate");
        assertThat(objectMapper.readValue("{\"providerReference\":\"CASE-1\",\"reason\":\"Checked\","
            + "\"outcome\":\"REFUNDED\"}", ExternalRefundReconciliationRequest.class)
            .outcome()).isEqualTo(ExternalRefundReconciliationRequest.Outcome.REFUNDED);
        assertThat(objectMapper.readValue("{\"scope\":\"local|/verified\",\"reason\":\"Evidence\"}",
            PrivacyScopeVerificationRequest.class).scope()).isEqualTo("local|/verified");
        assertThat(objectMapper.readValue("{\"status\":\"RESOLVED\"}", EmergencyAlertStatusRequest.class)
            .status()).isEqualTo("RESOLVED");
        assertThat(objectMapper.readValue("{\"consented\":true}", MediaConsentRequest.class).consented()).isTrue();
        EmergencyAlertRequest alert = objectMapper.readValue("{\"message\":\"No GPS\"}", EmergencyAlertRequest.class);
        assertThat(alert.getLatitude()).isNull();
        assertThat(alert.getLongitude()).isNull();
    }

    private void assertFields(JsonNode json, Set<String> expected) {
        assertThat(fieldNames(json)).isEqualTo(expected);
    }

    private Set<String> fieldNames(JsonNode node) {
        Set<String> names = new java.util.HashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
