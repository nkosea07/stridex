package com.racetracker.service;

import com.racetracker.dto.audit.AuditEventDto;
import com.racetracker.dto.audit.AuditEventPageDto;
import com.racetracker.exception.ApiException;
import com.racetracker.model.AuditEvent;
import com.racetracker.repository.AuditEventRepository;
import com.racetracker.repository.UserRepository;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuditEventService {

    private final AuditEventRepository repository;
    private final UserRepository userRepository;

    public AuditEventService(AuditEventRepository repository, UserRepository userRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String action, UUID actorUserId, String targetType, String targetId,
                       String outcome, Map<String, Object> metadata) {
        saveEvent(action, actorUserId, targetType, targetId, outcome, metadata);
    }

    @Transactional
    public void recordInCurrentTransaction(String action, UUID actorUserId, String targetType, String targetId,
                                          String outcome, Map<String, Object> metadata) {
        saveEvent(action, actorUserId, targetType, targetId, outcome, metadata);
    }

    @Transactional
    public void recordForActor(String action, String actorEmail, String targetType, String targetId,
                               String outcome, Map<String, Object> metadata) {
        UUID actorId = actorEmail == null ? null : userRepository.findByEmailIgnoreCase(actorEmail)
            .map(user -> user.getId()).orElse(null);
        saveEvent(action, actorId, targetType, targetId, outcome, metadata);
    }

    private void saveEvent(String action, UUID actorUserId, String targetType, String targetId,
                           String outcome, Map<String, Object> metadata) {
        repository.save(new AuditEvent(
            UUID.randomUUID(), actorUserId, action, targetType, targetId, outcome,
            MDC.get("requestId"), redact(metadata), OffsetDateTime.now()
        ));
    }

    @Transactional(readOnly = true)
    public AuditEventPageDto search(String action, String outcome, UUID actorUserId, int page, int size) {
        if (page < 0 || size < 1) {
            throw new ApiException("Page must be non-negative and size must be positive");
        }
        int boundedSize = Math.min(100, size);
        Page<AuditEvent> result = repository.search(
            blankToNull(action), blankToNull(outcome), actorUserId,
            PageRequest.of(page, boundedSize, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")))
        );
        return new AuditEventPageDto(result.getContent().stream().map(AuditEventService::toDto).toList(),
            result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    private static AuditEventDto toDto(AuditEvent event) {
        return new AuditEventDto(event.getId(), event.getActorUserId(), event.getAction(), event.getTargetType(),
            event.getTargetId(), event.getOutcome(), event.getRequestId(), event.getMetadata(), event.getCreatedAt());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Map<String, Object> redact(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) return Map.of();
        Map<String, Object> safe = new LinkedHashMap<>();
        metadata.forEach((key, value) -> {
            String normalized = key.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
            if (normalized.contains("password") || normalized.contains("secret") || normalized.contains("token")
                || normalized.contains("apikey") || normalized.contains("credential") || normalized.contains("recoverycode")
                || normalized.contains("email") || normalized.contains("phone") || normalized.contains("participantname")) {
                return;
            }
            if (value instanceof String || value instanceof Number || value instanceof Boolean) {
                safe.put(key, value);
            }
        });
        return Map.copyOf(safe);
    }
}
