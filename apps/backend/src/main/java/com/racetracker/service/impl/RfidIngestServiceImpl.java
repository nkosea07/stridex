package com.racetracker.service.impl;

import com.racetracker.dto.rfid.RfidReadEventBatchRequest;
import com.racetracker.dto.rfid.RfidReadEventBatchResponse;
import com.racetracker.dto.rfid.RfidReadEventRequest;
import com.racetracker.dto.rfid.RfidReadEventResultDto;
import com.racetracker.exception.UnauthorizedException;
import com.racetracker.model.CheckpointRecord;
import com.racetracker.model.Participant;
import com.racetracker.model.RfidReadEvent;
import com.racetracker.model.RfidReader;
import com.racetracker.repository.CheckpointRecordRepository;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.RfidReadEventRepository;
import com.racetracker.repository.RfidReaderRepository;
import com.racetracker.service.RfidApiKeyService;
import com.racetracker.service.RfidIngestService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class RfidIngestServiceImpl implements RfidIngestService {

    private final RfidReaderRepository rfidReaderRepository;
    private final RfidReadEventRepository rfidReadEventRepository;
    private final ParticipantRepository participantRepository;
    private final CheckpointRecordRepository checkpointRecordRepository;
    private final RfidApiKeyService rfidApiKeyService;

    public RfidIngestServiceImpl(
        RfidReaderRepository rfidReaderRepository,
        RfidReadEventRepository rfidReadEventRepository,
        ParticipantRepository participantRepository,
        CheckpointRecordRepository checkpointRecordRepository,
        RfidApiKeyService rfidApiKeyService
    ) {
        this.rfidReaderRepository = rfidReaderRepository;
        this.rfidReadEventRepository = rfidReadEventRepository;
        this.participantRepository = participantRepository;
        this.checkpointRecordRepository = checkpointRecordRepository;
        this.rfidApiKeyService = rfidApiKeyService;
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
            RfidReadEventResultDto result = processRead(reader, read);
            response.getResults().add(result);
            incrementCount(response, result.getStatus());
        }
        return response;
    }

    private RfidReadEventResultDto processRead(RfidReader reader, RfidReadEventRequest read) {
        if (reader.getStatus() != RfidReader.RfidReaderStatus.ACTIVE) {
            RfidReadEvent event = saveBaseEvent(reader, read, RfidReadEvent.RfidReadEventStatus.INVALID_READER);
            event.setErrorMessage("RFID reader is revoked");
            rfidReadEventRepository.save(event);
            return result(read, event, null, null, "RFID reader is revoked");
        }

        if (read.getSourceEventId() != null && !read.getSourceEventId().isBlank()) {
            Optional<RfidReadEvent> existingEvent = rfidReadEventRepository.findByReaderIdAndSourceEventId(
                reader.getId(),
                read.getSourceEventId().trim()
            );
            if (existingEvent.isPresent()) {
                RfidReadEvent duplicateEvent = saveBaseEvent(reader, read, RfidReadEvent.RfidReadEventStatus.DUPLICATE);
                duplicateEvent.setMatchedParticipant(existingEvent.get().getMatchedParticipant());
                duplicateEvent.setCheckpointRecord(existingEvent.get().getCheckpointRecord());
                duplicateEvent.setErrorMessage("sourceEventId already processed");
                rfidReadEventRepository.save(duplicateEvent);
                return result(read, duplicateEvent, duplicateEvent.getMatchedParticipant(), duplicateEvent.getCheckpointRecord(), duplicateEvent.getErrorMessage());
            }
        }

        Optional<Participant> participant = participantRepository.findByRaceIdAndRfidTag(reader.getRace().getId(), read.getRfidTag().trim());
        if (participant.isEmpty()) {
            RfidReadEvent event = saveBaseEvent(reader, read, RfidReadEvent.RfidReadEventStatus.UNKNOWN_TAG);
            event.setErrorMessage("No participant is assigned to RFID tag");
            rfidReadEventRepository.save(event);
            return result(read, event, null, null, event.getErrorMessage());
        }

        Optional<CheckpointRecord> existingRecord = checkpointRecordRepository.findByParticipantIdAndCheckpointId(
            participant.get().getId(),
            reader.getCheckpoint().getId()
        );
        if (existingRecord.isPresent()) {
            RfidReadEvent event = saveBaseEvent(reader, read, RfidReadEvent.RfidReadEventStatus.DUPLICATE);
            event.setMatchedParticipant(participant.get());
            event.setCheckpointRecord(existingRecord.get());
            event.setErrorMessage("Checkpoint already recorded for this participant");
            rfidReadEventRepository.save(event);
            return result(read, event, participant.get(), existingRecord.get(), event.getErrorMessage());
        }

        CheckpointRecord record = new CheckpointRecord();
        record.setRace(reader.getRace());
        record.setCheckpoint(reader.getCheckpoint());
        record.setParticipant(participant.get());
        record.setRecordedAt(read.getReadAt());
        record.setRecordMethod(CheckpointRecord.RecordMethod.RFID);
        record.setIsVerified(true);
        CheckpointRecord savedRecord = checkpointRecordRepository.save(record);

        RfidReadEvent event = saveBaseEvent(reader, read, RfidReadEvent.RfidReadEventStatus.ACCEPTED);
        event.setMatchedParticipant(participant.get());
        event.setCheckpointRecord(savedRecord);
        rfidReadEventRepository.save(event);
        return result(read, event, participant.get(), savedRecord, null);
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
        event.setStatus(status);
        return event;
    }

    private RfidReadEventResultDto result(
        RfidReadEventRequest read,
        RfidReadEvent event,
        Participant participant,
        CheckpointRecord checkpointRecord,
        String errorMessage
    ) {
        RfidReadEventResultDto dto = new RfidReadEventResultDto();
        dto.setSourceEventId(read.getSourceEventId());
        dto.setRfidTag(read.getRfidTag());
        dto.setStatus(event.getStatus().name());
        dto.setParticipantId(participant != null ? participant.getId() : null);
        dto.setCheckpointRecordId(checkpointRecord != null ? checkpointRecord.getId() : null);
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
