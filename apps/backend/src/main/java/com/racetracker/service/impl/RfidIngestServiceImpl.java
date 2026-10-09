package com.racetracker.service.impl;

import com.racetracker.dto.rfid.RfidReadEventBatchRequest;
import com.racetracker.dto.rfid.RfidReadEventBatchResponse;
import com.racetracker.dto.rfid.RfidReadEventRequest;
import com.racetracker.dto.rfid.RfidReadEventResultDto;
import com.racetracker.exception.UnauthorizedException;
import com.racetracker.model.RfidReadEvent;
import com.racetracker.model.RfidReader;
import com.racetracker.repository.RfidReadEventRepository;
import com.racetracker.repository.RfidReaderRepository;
import com.racetracker.service.RfidEventPublisher;
import com.racetracker.service.RfidApiKeyService;
import com.racetracker.service.RfidIngestService;
import com.racetracker.config.RaceTrackerProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class RfidIngestServiceImpl implements RfidIngestService {

    private final RfidReaderRepository rfidReaderRepository;
    private final RfidReadEventRepository rfidReadEventRepository;
    private final RfidApiKeyService rfidApiKeyService;
    private final RfidEventPublisher rfidEventPublisher;
    private final RaceTrackerProperties raceTrackerProperties;

    public RfidIngestServiceImpl(
        RfidReaderRepository rfidReaderRepository,
        RfidReadEventRepository rfidReadEventRepository,
        RfidApiKeyService rfidApiKeyService,
        RfidEventPublisher rfidEventPublisher,
        RaceTrackerProperties raceTrackerProperties
    ) {
        this.rfidReaderRepository = rfidReaderRepository;
        this.rfidReadEventRepository = rfidReadEventRepository;
        this.rfidApiKeyService = rfidApiKeyService;
        this.rfidEventPublisher = rfidEventPublisher;
        this.raceTrackerProperties = raceTrackerProperties;
    }

    @Override
    @Transactional
    public RfidReadEventBatchResponse ingest(String apiKey, RfidReadEventBatchRequest request, String sourceIp) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new UnauthorizedException("RFID reader API key is required");
        }

        RfidReader reader = rfidReaderRepository.findByApiKeyHash(rfidApiKeyService.hashKey(apiKey.trim()))
            .orElseThrow(() -> new UnauthorizedException("Invalid RFID reader API key"));

        if (reader.getStatus() == RfidReader.RfidReaderStatus.ACTIVE) {
            reader.setLastSeenAt(OffsetDateTime.now());
            reader.setLastIp(sourceIp);
            rfidReaderRepository.save(reader);
        }

        RfidReadEventBatchResponse response = new RfidReadEventBatchResponse();
        List<RfidReadEventRequest> reads = request.getReads();
        for (RfidReadEventRequest read : reads) {
            if (reader.getStatus() != RfidReader.RfidReaderStatus.ACTIVE) {
                RfidReadEvent event = saveBaseEvent(reader, read, RfidReadEvent.RfidReadEventStatus.INVALID_READER);
                event.setErrorMessage("RFID reader is revoked");
                RfidReadEvent saved = rfidReadEventRepository.save(event);
                RfidReadEventResultDto result = result(read, saved.getStatus().name(), null, null, "RFID reader is revoked");
                response.getResults().add(result);
                incrementCount(response, result.getStatus());
                continue;
            }

            String sourceEventId = read.getSourceEventId() == null ? null : read.getSourceEventId().trim();
            if (sourceEventId != null && !sourceEventId.isBlank()) {
                Optional<RfidReadEvent> existing = rfidReadEventRepository.findByReaderIdAndSourceEventId(
                    reader.getId(),
                    sourceEventId
                );
                if (existing.isPresent()) {
                    RfidReadEventResultDto result = result(
                        read,
                        RfidReadEvent.RfidReadEventStatus.DUPLICATE.name(),
                        existing.get().getMatchedParticipant() == null ? null : existing.get().getMatchedParticipant().getId(),
                        existing.get().getCheckpointRecord() == null ? null : existing.get().getCheckpointRecord().getId(),
                        "sourceEventId already ingested"
                    );
                    response.getResults().add(result);
                    incrementCount(response, result.getStatus());
                    continue;
                }
            }

            RfidReadEvent event = saveBaseEvent(reader, read, RfidReadEvent.RfidReadEventStatus.RECEIVED);
            RfidReadEvent saved = rfidReadEventRepository.save(event);
            rfidEventPublisher.publish(saved);

            RfidReadEventResultDto result = result(read, "ACCEPTED", null, null, null);
            response.getResults().add(result);
            incrementCount(response, result.getStatus());
        }
        return response;
    }

    private RfidReadEvent saveBaseEvent(
        RfidReader reader,
        RfidReadEventRequest read,
        RfidReadEvent.RfidReadEventStatus status
    ) {
        RfidReadEvent event = new RfidReadEvent();
        event.setReader(reader);
        event.setRace(reader.getRace());
        event.setCheckpoint(reader.getCheckpoint());
        event.setRfidTag(read.getRfidTag().trim());
        event.setSourceEventId(read.getSourceEventId() != null && !read.getSourceEventId().isBlank()
            ? read.getSourceEventId().trim()
            : null);
        event.setReadAt(read.getReadAt());
        event.setAntenna(read.getAntenna());
        event.setRssi(read.getRssi());
        event.setRawPayload(read.getRawPayload());
        event.setEventOrigin(normalizeEventOrigin(read.getEventOrigin()));
        event.setStatus(status);
        return event;
    }

    private String normalizeEventOrigin(String rawOrigin) {
        String origin = rawOrigin == null || rawOrigin.isBlank() ? "HARDWARE" : rawOrigin.trim().toUpperCase(java.util.Locale.ROOT);
        if (!origin.equals("SIMULATED") && !origin.equals("HARDWARE")) {
            throw new com.racetracker.exception.ApiException("RFID event origin must be SIMULATED or HARDWARE");
        }
        if (origin.equals("SIMULATED") && !raceTrackerProperties.getRfid().isSimulationEnabled()) {
            throw new com.racetracker.exception.ApiException("Simulated RFID events are disabled");
        }
        return origin;
    }

    private RfidReadEventResultDto result(RfidReadEventRequest read, String status, java.util.UUID participantId, java.util.UUID checkpointRecordId, String errorMessage) {
        RfidReadEventResultDto dto = new RfidReadEventResultDto();
        dto.setSourceEventId(read.getSourceEventId());
        dto.setRfidTag(read.getRfidTag());
        dto.setStatus(status);
        dto.setParticipantId(participantId);
        dto.setCheckpointRecordId(checkpointRecordId);
        dto.setErrorMessage(errorMessage);
        return dto;
    }

    private void incrementCount(RfidReadEventBatchResponse response, String status) {
        RfidReadEvent.RfidReadEventStatus eventStatus = RfidReadEvent.RfidReadEventStatus.valueOf(status);
        switch (eventStatus) {
            case ACCEPTED -> response.setAccepted(response.getAccepted() + 1);
            case DUPLICATE -> response.setDuplicate(response.getDuplicate() + 1);
            case UNKNOWN_TAG -> response.setUnknownTag(response.getUnknownTag() + 1);
            case INVALID_READER -> response.setInvalidReader(response.getInvalidReader() + 1);
            case ERROR -> response.setError(response.getError() + 1);
        }
    }
}
