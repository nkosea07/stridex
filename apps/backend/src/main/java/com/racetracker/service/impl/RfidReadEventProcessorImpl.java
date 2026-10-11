package com.racetracker.service.impl;

import com.racetracker.model.Checkpoint;
import com.racetracker.model.CheckpointRecord;
import com.racetracker.model.Participant;
import com.racetracker.model.ParticipantCredential;
import com.racetracker.model.RfidReadEvent;
import com.racetracker.model.RfidReader;
import com.racetracker.repository.CheckpointRecordRepository;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.RfidReadEventRepository;
import com.racetracker.repository.RfidReaderRepository;
import com.racetracker.service.ParticipantCredentialService;
import com.racetracker.service.LiveSummaryCacheInvalidator;
import com.racetracker.service.RfidReadEventProcessor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
public class RfidReadEventProcessorImpl implements RfidReadEventProcessor {

    private final RfidReadEventRepository rfidReadEventRepository;
    private final CheckpointRecordRepository checkpointRecordRepository;
    private final ParticipantRepository participantRepository;
    private final RfidReaderRepository rfidReaderRepository;
    private final ParticipantCredentialService participantCredentialService;
    private final RfidIngestMetrics rfidIngestMetrics;
    private final LiveSummaryCacheInvalidator liveSummaryCacheInvalidator;

    public RfidReadEventProcessorImpl(
        RfidReadEventRepository rfidReadEventRepository,
        CheckpointRecordRepository checkpointRecordRepository,
        ParticipantRepository participantRepository,
        RfidReaderRepository rfidReaderRepository,
        ParticipantCredentialService participantCredentialService,
        RfidIngestMetrics rfidIngestMetrics,
        LiveSummaryCacheInvalidator liveSummaryCacheInvalidator
    ) {
        this.rfidReadEventRepository = rfidReadEventRepository;
        this.checkpointRecordRepository = checkpointRecordRepository;
        this.participantRepository = participantRepository;
        this.rfidReaderRepository = rfidReaderRepository;
        this.participantCredentialService = participantCredentialService;
        this.rfidIngestMetrics = rfidIngestMetrics;
        this.liveSummaryCacheInvalidator = liveSummaryCacheInvalidator;
    }

    @Override
    @Transactional
    public void process(UUID eventId) {
        RfidReadEvent event = rfidReadEventRepository.findWithRelationsById(eventId).orElse(null);
        if (event == null || event.getStatus() != RfidReadEvent.RfidReadEventStatus.RECEIVED) {
            return;
        }

        RfidReader reader = event.getReader();
        if (reader == null || reader.getStatus() != RfidReader.RfidReaderStatus.ACTIVE) {
            markEvent(event, RfidReadEvent.RfidReadEventStatus.INVALID_READER, null, null, "RFID reader is revoked");
            rfidIngestMetrics.incrementInvalidReader();
            markReaderProcessed(reader);
            return;
        }

        if (event.getSourceEventId() != null && !event.getSourceEventId().isBlank()) {
            Optional<RfidReadEvent> duplicateSource = rfidReadEventRepository.findFirstByReaderIdAndSourceEventIdAndIdNotOrderByReceivedAtAscIdAsc(
                reader.getId(),
                event.getSourceEventId(),
                event.getId()
            );
            if (duplicateSource.isPresent() && duplicateSource.get().getReceivedAt() != null
                && duplicateSource.get().getReceivedAt().isBefore(event.getReceivedAt())) {
                markEvent(event, RfidReadEvent.RfidReadEventStatus.DUPLICATE,
                    duplicateSource.get().getMatchedParticipant(),
                    duplicateSource.get().getCheckpointRecord(),
                    "sourceEventId already processed");
                rfidIngestMetrics.incrementDuplicate();
                markReaderProcessed(reader);
                return;
            }
        }

        Participant participant = participantCredentialService.resolveTimingParticipant(
            ParticipantCredential.CredentialType.RFID_UHF_TIMING,
            null,
            reader.getRace().getId(),
            event.getRfidTag()
        );
        if (participant == null) {
            markEvent(event, RfidReadEvent.RfidReadEventStatus.UNKNOWN_TAG, null, null, "No participant is assigned to RFID tag");
            rfidIngestMetrics.incrementUnknownCredential();
            markReaderProcessed(reader);
            return;
        }

        Optional<CheckpointRecord> existingRecord = checkpointRecordRepository.findByParticipantIdAndCheckpointId(
            participant.getId(),
            reader.getCheckpoint().getId()
        );
        if (existingRecord.isPresent()) {
            markEvent(event, RfidReadEvent.RfidReadEventStatus.DUPLICATE, participant, existingRecord.get(),
                "Checkpoint already recorded for this participant");
            rfidIngestMetrics.incrementDuplicate();
            markReaderProcessed(reader);
            return;
        }

        CheckpointRecord record = new CheckpointRecord();
        record.setRace(reader.getRace());
        record.setCheckpoint(reader.getCheckpoint());
        record.setParticipant(participant);
        record.setRecordedAt(event.getReadAt());
        record.setRecordMethod(CheckpointRecord.RecordMethod.RFID);
        record.setIsVerified(true);
        CheckpointRecord savedRecord = checkpointRecordRepository.save(record);
        liveSummaryCacheInvalidator.evictLastCheckpointAfterCommit(participant.getId(), reader.getRace().getId());

        updateParticipantStatusForCheckpoint(participant, reader.getCheckpoint());
        participantRepository.save(participant);

        markReaderProcessed(reader);
        markEvent(event, RfidReadEvent.RfidReadEventStatus.ACCEPTED, participant, savedRecord, null);
    }

    private void markEvent(
        RfidReadEvent event,
        RfidReadEvent.RfidReadEventStatus status,
        Participant participant,
        CheckpointRecord checkpointRecord,
        String errorMessage
    ) {
        event.setStatus(status);
        event.setMatchedParticipant(participant);
        event.setCheckpointRecord(checkpointRecord);
        event.setErrorMessage(errorMessage);
        rfidReadEventRepository.save(event);
    }

    private void updateParticipantStatusForCheckpoint(Participant participant, Checkpoint checkpoint) {
        if (checkpoint.getCheckpointType() == Checkpoint.CheckpointType.FINISH) {
            participant.setStatus(Participant.ParticipantStatus.FINISHED);
            return;
        }
        if (participant.getStatus() == Participant.ParticipantStatus.REGISTERED
            || participant.getStatus() == Participant.ParticipantStatus.CONFIRMED
            || participant.getStatus() == Participant.ParticipantStatus.CHECKED_IN) {
            participant.setStatus(Participant.ParticipantStatus.STARTED);
        }
    }

    private void markReaderProcessed(RfidReader reader) {
        if (reader == null) {
            return;
        }
        reader.setLastProcessedAt(OffsetDateTime.now());
        if (reader.getStatus() == RfidReader.RfidReaderStatus.ACTIVE) {
            reader.setLastError(null);
        }
        rfidReaderRepository.save(reader);
    }
}
