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
import com.racetracker.service.CheckpointCutoff;
import com.racetracker.service.CheckpointProgression;
import com.racetracker.service.LiveSummaryCacheInvalidator;
import com.racetracker.service.RfidReadEventProcessor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class RfidReadEventProcessorImpl implements RfidReadEventProcessor {

    static final int MAX_PROCESSING_ATTEMPTS = 8;
    private static final java.time.Duration INITIAL_BACKOFF = java.time.Duration.ofSeconds(5);
    private static final java.time.Duration MAX_BACKOFF = java.time.Duration.ofMinutes(5);

    private final RfidReadEventRepository rfidReadEventRepository;
    private final CheckpointRecordRepository checkpointRecordRepository;
    private final ParticipantRepository participantRepository;
    private final RfidReaderRepository rfidReaderRepository;
    private final ParticipantCredentialService participantCredentialService;
    private final RfidIngestMetrics rfidIngestMetrics;
    private final LiveSummaryCacheInvalidator liveSummaryCacheInvalidator;
    private final CheckpointProgression checkpointProgression;

    public RfidReadEventProcessorImpl(
        RfidReadEventRepository rfidReadEventRepository,
        CheckpointRecordRepository checkpointRecordRepository,
        ParticipantRepository participantRepository,
        RfidReaderRepository rfidReaderRepository,
        ParticipantCredentialService participantCredentialService,
        RfidIngestMetrics rfidIngestMetrics,
        LiveSummaryCacheInvalidator liveSummaryCacheInvalidator,
        CheckpointProgression checkpointProgression
    ) {
        this.rfidReadEventRepository = rfidReadEventRepository;
        this.checkpointRecordRepository = checkpointRecordRepository;
        this.participantRepository = participantRepository;
        this.rfidReaderRepository = rfidReaderRepository;
        this.participantCredentialService = participantCredentialService;
        this.rfidIngestMetrics = rfidIngestMetrics;
        this.liveSummaryCacheInvalidator = liveSummaryCacheInvalidator;
        this.checkpointProgression = checkpointProgression;
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

        if (!CheckpointProgression.isTimeableEntrant(participant.getStatus())) {
            markEvent(event, RfidReadEvent.RfidReadEventStatus.ERROR, participant, null,
                "Participant is not an accepted entrant for this race");
            rfidIngestMetrics.incrementError();
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
        boolean cutoffMissed = evaluateCutoff(
            reader.getCheckpoint(), participant, reader.getRace().getId(), event.getReadAt());
        record.setIsCutoffMissed(cutoffMissed);
        CheckpointRecord savedRecord = checkpointRecordRepository.save(record);
        liveSummaryCacheInvalidator.evictLastCheckpointAfterCommit(participant.getId(), reader.getRace().getId());

        checkpointProgression.apply(participant, reader.getCheckpoint(), cutoffMissed);
        participantRepository.save(participant);

        markReaderProcessed(reader);
        markEvent(event, RfidReadEvent.RfidReadEventStatus.ACCEPTED, participant, savedRecord, null);
    }

    @Override
    @Transactional
    public boolean recordProcessingFailure(UUID eventId, Exception failure) {
        RfidReadEvent event = rfidReadEventRepository.findById(eventId).orElse(null);
        if (event == null || event.getStatus() != RfidReadEvent.RfidReadEventStatus.RECEIVED) {
            return false;
        }
        int attempts = (event.getProcessingAttempts() == null ? 0 : event.getProcessingAttempts()) + 1;
        event.setProcessingAttempts(attempts);
        String failureType = failure == null ? "UnknownError" : failure.getClass().getSimpleName();
        if (attempts >= MAX_PROCESSING_ATTEMPTS) {
            event.setStatus(RfidReadEvent.RfidReadEventStatus.ERROR);
            event.setNextAttemptAt(null);
            event.setErrorMessage("Processing failed after " + attempts + " attempts (" + failureType + ")");
            rfidReadEventRepository.save(event);
            return true;
        }
        long backoffSeconds = Math.min(MAX_BACKOFF.toSeconds(), INITIAL_BACKOFF.toSeconds() << Math.min(attempts - 1, 10));
        event.setNextAttemptAt(OffsetDateTime.now().plusSeconds(backoffSeconds));
        event.setErrorMessage("Processing attempt " + attempts + " failed (" + failureType + ")");
        rfidReadEventRepository.save(event);
        return false;
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

    private boolean evaluateCutoff(Checkpoint checkpoint, Participant participant, UUID raceId, OffsetDateTime recordedAt) {
        if (checkpoint.getCutoffTime() == null) {
            return false;
        }
        List<CheckpointRecord> existing = checkpointRecordRepository
            .findByParticipantIdAndRaceIdOrderByRecordedAtAsc(participant.getId(), raceId);
        OffsetDateTime start = CheckpointCutoff.resolveStart(participant, existing);
        return CheckpointCutoff.isMissed(checkpoint.getCutoffTime(), start, recordedAt);
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
