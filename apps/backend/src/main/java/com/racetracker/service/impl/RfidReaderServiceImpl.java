package com.racetracker.service.impl;

import com.racetracker.dto.rfid.RfidReaderCreateRequest;
import com.racetracker.dto.rfid.RfidReaderCreateResponse;
import com.racetracker.dto.rfid.RfidReaderDto;
import com.racetracker.dto.rfid.RfidReaderHeartbeatRequest;
import com.racetracker.dto.rfid.RfidRaceOverviewDto;
import com.racetracker.exception.ApiException;
import com.racetracker.exception.ConflictException;
import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.exception.UnauthorizedException;
import com.racetracker.model.Checkpoint;
import com.racetracker.model.Race;
import com.racetracker.model.RfidReadEvent;
import com.racetracker.model.RfidReader;
import com.racetracker.repository.RfidReadEventRepository;
import com.racetracker.repository.CheckpointRepository;
import com.racetracker.repository.RaceRepository;
import com.racetracker.repository.RfidReaderRepository;
import com.racetracker.config.RaceTrackerProperties;
import com.racetracker.service.RfidApiKeyService;
import com.racetracker.service.RfidReaderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class RfidReaderServiceImpl implements RfidReaderService {

    private final RfidReaderRepository rfidReaderRepository;
    private final RaceRepository raceRepository;
    private final CheckpointRepository checkpointRepository;
    private final RfidReadEventRepository rfidReadEventRepository;
    private final RfidApiKeyService rfidApiKeyService;
    private final RaceTrackerProperties raceTrackerProperties;

    public RfidReaderServiceImpl(
        RfidReaderRepository rfidReaderRepository,
        RaceRepository raceRepository,
        CheckpointRepository checkpointRepository,
        RfidReadEventRepository rfidReadEventRepository,
        RfidApiKeyService rfidApiKeyService,
        RaceTrackerProperties raceTrackerProperties
    ) {
        this.rfidReaderRepository = rfidReaderRepository;
        this.raceRepository = raceRepository;
        this.checkpointRepository = checkpointRepository;
        this.rfidReadEventRepository = rfidReadEventRepository;
        this.rfidApiKeyService = rfidApiKeyService;
        this.raceTrackerProperties = raceTrackerProperties;
    }

    @Override
    @Transactional
    public RfidReaderCreateResponse create(UUID raceId, RfidReaderCreateRequest request) {
        Race race = raceRepository.findById(raceId)
            .orElseThrow(() -> new ResourceNotFoundException("Race not found"));
        Checkpoint checkpoint = checkpointRepository.findByRaceIdAndId(raceId, request.getCheckpointId())
            .orElseThrow(() -> new ResourceNotFoundException("Checkpoint not found"));

        if (!Boolean.TRUE.equals(checkpoint.getHasRfidReader())) {
            throw new ApiException("Checkpoint is not configured for RFID reads");
        }
        String externalId = request.getExternalId().trim();
        if (rfidReaderRepository.existsByRaceIdAndExternalId(raceId, externalId)) {
            throw new ConflictException("RFID reader externalId already exists for this race");
        }

        String apiKey = rfidApiKeyService.generateKey();
        RfidReader reader = new RfidReader();
        reader.setRace(race);
        reader.setCheckpoint(checkpoint);
        reader.setName(request.getName().trim());
        reader.setExternalId(externalId);
        reader.setApiKeyHash(rfidApiKeyService.hashKey(apiKey));
        reader.setStatus(RfidReader.RfidReaderStatus.ACTIVE);

        RfidReader saved = rfidReaderRepository.save(reader);
        RfidReaderCreateResponse response = new RfidReaderCreateResponse();
        copyReader(saved, response);
        response.setApiKey(apiKey);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RfidReaderDto> list(UUID raceId) {
        if (!raceRepository.existsById(raceId)) {
            throw new ResourceNotFoundException("Race not found");
        }
        return rfidReaderRepository.findByRaceIdOrderByCreatedAtDesc(raceId)
            .stream()
            .map(this::toDto)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public RfidRaceOverviewDto overview(UUID raceId) {
        if (!raceRepository.existsById(raceId)) {
            throw new ResourceNotFoundException("Race not found");
        }

        List<RfidReader> readers = rfidReaderRepository.findByRaceIdOrderByCreatedAtDesc(raceId);
        long totalEvents = rfidReadEventRepository.countByRaceId(raceId);
        long queueDepth = rfidReadEventRepository.countByRaceIdAndStatus(raceId, RfidReadEvent.RfidReadEventStatus.RECEIVED);
        long duplicateCount = rfidReadEventRepository.countByRaceIdAndStatus(raceId, RfidReadEvent.RfidReadEventStatus.DUPLICATE);
        long unknownCredentialCount = rfidReadEventRepository.countByRaceIdAndStatus(raceId, RfidReadEvent.RfidReadEventStatus.UNKNOWN_TAG);
        long invalidReaderCount = rfidReadEventRepository.countByRaceIdAndStatus(raceId, RfidReadEvent.RfidReadEventStatus.INVALID_READER);
        long errorCount = rfidReadEventRepository.countByRaceIdAndStatus(raceId, RfidReadEvent.RfidReadEventStatus.ERROR);
        long processedEvents = Math.max(totalEvents - queueDepth, 0);

        RfidRaceOverviewDto dto = new RfidRaceOverviewDto();
        dto.setTotalReaders(readers.size());
        dto.setActiveReaders((int) readers.stream().filter(reader -> reader.getStatus() == RfidReader.RfidReaderStatus.ACTIVE).count());
        dto.setOnlineReaders((int) readers.stream().filter(reader -> deriveGatewayStatus(reader) == RfidReader.GatewayStatus.ONLINE).count());
        dto.setDegradedReaders((int) readers.stream().filter(reader -> deriveGatewayStatus(reader) == RfidReader.GatewayStatus.DEGRADED).count());
        dto.setOfflineReaders((int) readers.stream().filter(reader -> deriveGatewayStatus(reader) == RfidReader.GatewayStatus.OFFLINE).count());
        dto.setTotalEvents(totalEvents);
        dto.setProcessedEvents(processedEvents);
        dto.setQueueDepth(queueDepth);
        dto.setDuplicateCount(duplicateCount);
        dto.setUnknownCredentialCount(unknownCredentialCount);
        dto.setInvalidReaderCount(invalidReaderCount);
        dto.setErrorCount(errorCount);
        dto.setDuplicateRate(rate(duplicateCount, processedEvents));
        dto.setUnknownCredentialRate(rate(unknownCredentialCount, processedEvents));
        dto.setAverageIngressLagMillis(rfidReadEventRepository.findAverageIngressLagMillis(raceId));
        dto.setOldestQueuedReadAgeSeconds(oldestQueuedAgeSeconds(raceId));
        dto.setReaderSilenceTimeoutSeconds(raceTrackerProperties.getRfid().getReaderSilenceTimeoutSeconds());
        dto.setGatewayBacklogDegradedThreshold(raceTrackerProperties.getRfid().getGatewayBacklogDegradedThreshold());
        return dto;
    }

    @Override
    @Transactional
    public RfidReaderDto revoke(UUID raceId, UUID readerId) {
        RfidReader reader = rfidReaderRepository.findByRaceIdAndId(raceId, readerId)
            .orElseThrow(() -> new ResourceNotFoundException("RFID reader not found"));
        reader.setStatus(RfidReader.RfidReaderStatus.REVOKED);
        return toDto(rfidReaderRepository.save(reader));
    }

    @Override
    @Transactional
    public RfidReaderDto heartbeat(String apiKey, RfidReaderHeartbeatRequest request, String sourceIp) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new UnauthorizedException("RFID reader API key is required");
        }
        RfidReader reader = rfidReaderRepository.findByApiKeyHash(rfidApiKeyService.hashKey(apiKey.trim()))
            .orElseThrow(() -> new UnauthorizedException("Invalid RFID reader API key"));

        OffsetDateTime observedAt = request.getObservedAt() != null ? request.getObservedAt() : OffsetDateTime.now();
        reader.setLastSeenAt(observedAt);
        reader.setLastHeartbeatAt(observedAt);
        reader.setLastIp(sourceIp);
        reader.setGatewayName(trimToNull(request.getGatewayName()));
        reader.setGatewayBacklog(request.getGatewayBacklog() != null ? request.getGatewayBacklog() : 0);
        reader.setFirmwareVersion(trimToNull(request.getFirmwareVersion()));
        reader.setHardwareModel(trimToNull(request.getHardwareModel()));
        reader.setHardwareProfile(trimToNull(request.getHardwareProfile()));
        reader.setReaderClass(trimToNull(request.getReaderClass()));
        reader.setLastError(trimToNull(request.getLastError()));
        reader.setGatewayStatus(parseGatewayStatus(request.getGatewayStatus(), reader.getGatewayBacklog(), reader.getLastError()));

        return toDto(rfidReaderRepository.save(reader));
    }

    private RfidReaderDto toDto(RfidReader reader) {
        RfidReaderDto dto = new RfidReaderDto();
        copyReader(reader, dto);
        return dto;
    }

    private void copyReader(RfidReader reader, RfidReaderDto dto) {
        dto.setId(reader.getId());
        dto.setRaceId(reader.getRace() != null ? reader.getRace().getId() : null);
        dto.setCheckpointId(reader.getCheckpoint() != null ? reader.getCheckpoint().getId() : null);
        dto.setName(reader.getName());
        dto.setExternalId(reader.getExternalId());
        dto.setStatus(reader.getStatus() != null ? reader.getStatus().name() : null);
        dto.setLastSeenAt(reader.getLastSeenAt() != null ? reader.getLastSeenAt().toString() : null);
        dto.setLastIp(reader.getLastIp());
        dto.setGatewayName(reader.getGatewayName());
        dto.setGatewayStatus(deriveGatewayStatus(reader).name());
        dto.setGatewayBacklog(reader.getGatewayBacklog());
        dto.setFirmwareVersion(reader.getFirmwareVersion());
        dto.setHardwareModel(reader.getHardwareModel());
        dto.setHardwareProfile(reader.getHardwareProfile());
        dto.setReaderClass(reader.getReaderClass());
        dto.setLastError(reader.getLastError());
        dto.setLastHeartbeatAt(reader.getLastHeartbeatAt() != null ? reader.getLastHeartbeatAt().toString() : null);
        dto.setLastProcessedAt(reader.getLastProcessedAt() != null ? reader.getLastProcessedAt().toString() : null);
        dto.setCreatedAt(reader.getCreatedAt() != null ? reader.getCreatedAt().toString() : null);
        dto.setUpdatedAt(reader.getUpdatedAt() != null ? reader.getUpdatedAt().toString() : null);
    }

    private RfidReader.GatewayStatus deriveGatewayStatus(RfidReader reader) {
        OffsetDateTime lastHeartbeatAt = reader.getLastHeartbeatAt();
        if (lastHeartbeatAt == null) {
            return RfidReader.GatewayStatus.OFFLINE;
        }
        long silenceSeconds = raceTrackerProperties.getRfid().getReaderSilenceTimeoutSeconds();
        if (Duration.between(lastHeartbeatAt, OffsetDateTime.now()).getSeconds() > silenceSeconds) {
            return RfidReader.GatewayStatus.OFFLINE;
        }
        return reader.getGatewayStatus() != null ? reader.getGatewayStatus() : RfidReader.GatewayStatus.ONLINE;
    }

    private RfidReader.GatewayStatus parseGatewayStatus(String rawStatus, Integer backlog, String lastError) {
        if (rawStatus != null && !rawStatus.isBlank()) {
            try {
                return RfidReader.GatewayStatus.valueOf(rawStatus.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // Fall through to inferred status.
            }
        }
        if (lastError != null && !lastError.isBlank()) {
            return RfidReader.GatewayStatus.DEGRADED;
        }
        if (backlog != null && backlog > raceTrackerProperties.getRfid().getGatewayBacklogDegradedThreshold()) {
            return RfidReader.GatewayStatus.DEGRADED;
        }
        return RfidReader.GatewayStatus.ONLINE;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private double rate(long numerator, long denominator) {
        if (denominator <= 0) {
            return 0;
        }
        return (numerator * 100.0) / denominator;
    }

    private Long oldestQueuedAgeSeconds(UUID raceId) {
        OffsetDateTime oldestQueuedRead = rfidReadEventRepository.findOldestReceivedAtByRaceIdAndStatus(
            raceId,
            RfidReadEvent.RfidReadEventStatus.RECEIVED
        );
        if (oldestQueuedRead == null) {
            return 0L;
        }
        return Math.max(Duration.between(oldestQueuedRead, OffsetDateTime.now()).getSeconds(), 0);
    }
}
