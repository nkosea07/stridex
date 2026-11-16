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
    private final com.racetracker.service.CutoffReevaluation cutoffReevaluation;
    private final com.racetracker.service.TrackingService trackingService;
    private final com.racetracker.config.RaceTrackerProperties properties;

    public RfidReadEventProcessorImpl(
        RfidReadEventRepository rfidReadEventRepository,
        CheckpointRecordRepository checkpointRecordRepository,
        ParticipantRepository participantRepository,
        RfidReaderRepository rfidReaderRepository,
        ParticipantCredentialService participantCredentialService,
        RfidIngestMetrics rfidIngestMetrics,
        LiveSummaryCacheInvalidator liveSummaryCacheInvalidator,
        CheckpointProgression checkpointProgression,
        com.racetracker.service.CutoffReevaluation cutoffReevaluation,
        com.racetracker.service.TrackingService trackingService,
        com.racetracker.config.RaceTrackerProperties properties
    ) {
        this.rfidReadEventRepository = rfidReadEventRepository;
        this.checkpointRecordRepository = checkpointRecordRepository;
        this.participantRepository = participantRepository;
        this.rfidReaderRepository = rfidReaderRepository;
        this.participantCredentialService = participantCredentialService;
        this.rfidIngestMetrics = rfidIngestMetrics;
        this.liveSummaryCacheInvalidator = liveSummaryCacheInvalidator;
        this.checkpointProgression = checkpointProgression;
        this.cutoffReevaluation = cutoffReevaluation;
        this.trackingService = trackingService;
        this.properties = properties;
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

        String ignoredReason = timingWindowViolation(event, reader);
        if (ignoredReason != null) {
            markEvent(event, RfidReadEvent.RfidReadEventStatus.IGNORED, null, null, ignoredReason);
            rfidIngestMetrics.incrementIgnored();
            markReaderProcessed(reader);
            return;
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

        String preGun = preGunViolation(event, participant);
        if (preGun != null) {
            markEvent(event, RfidReadEvent.RfidReadEventStatus.IGNORED, participant, null, preGun);
            rfidIngestMetrics.incrementIgnored();
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

        if (reader.getCheckpoint().passes() > 1) {
            processMultiPass(event, reader, participant);
            return;
        }

        Optional<CheckpointRecord> existingRecord = checkpointRecordRepository.findFirstByParticipantIdAndCheckpointIdOrderByPassNumberAsc(
            participant.getId(),
            reader.getCheckpoint().getId()
        );
        if (existingRecord.isPresent()) {
            CheckpointRecord current = existingRecord.get();
            if (isEarlierAutomaticRead(current, event)) {
                current.setRecordedAt(event.getReadAt());
                checkpointRecordRepository.save(current);
                cutoffReevaluation.reevaluate(participant, reader.getRace().getId());
                liveSummaryCacheInvalidator.evictLastCheckpointAfterCommit(participant.getId(), reader.getRace().getId());
                trackingService.publishTimingUpdate(reader.getRace().getId(), participant.getId());
                markEvent(event, RfidReadEvent.RfidReadEventStatus.ACCEPTED, participant, current,
                    "Earlier read replaced the checkpoint time");
                rfidIngestMetrics.incrementEarlierRead();
                markReaderProcessed(reader);
                return;
            }
            markEvent(event, RfidReadEvent.RfidReadEventStatus.DUPLICATE, participant, current,
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
        trackingService.publishTimingUpdate(reader.getRace().getId(), participant.getId());

        markReaderProcessed(reader);
        markEvent(event, RfidReadEvent.RfidReadEventStatus.ACCEPTED, participant, savedRecord, null);
    }

    /**
     * Reads that are kept for audit and reprocessing but are not timing reads: a reader clock
     * ahead of the server (the read claims to be from the future), or a read before the gun
     * (tag tests at packet pickup, warm-ups through the finish chute, athletes waiting on the
     * start mat). The gun is the recorded race start, falling back to the scheduled start.
     */
    private String timingWindowViolation(RfidReadEvent event, RfidReader reader) {
        if (event.getReadAt() == null) {
            return "Read has no timestamp";
        }
        OffsetDateTime receivedAt = event.getReceivedAt() != null ? event.getReceivedAt() : OffsetDateTime.now();
        long maxFutureMs = Math.max(properties.getRfid().getMaxFutureReadSkewMs(), 0);
        if (event.getReadAt().isAfter(receivedAt.plus(java.time.Duration.ofMillis(maxFutureMs)))) {
            return "Read time is ahead of the server clock; check the reader and gateway clock (NTP)";
        }
        com.racetracker.model.Race race = reader.getRace();
        if (com.racetracker.service.ResultLock.isCertified(race)) {
            return "Results are certified official; reopen the results to apply new reads";
        }
        return null;
    }

    /** Reads before the participant's own gun (their wave, else the race) are not timing reads. */
    private String preGunViolation(RfidReadEvent event, Participant participant) {
        OffsetDateTime gun = CheckpointCutoff.gunStart(participant);
        if (gun != null && event.getReadAt().isBefore(gun)) {
            return "Read before the race start";
        }
        return null;
    }

    /**
     * The first read of a pass is the crossing time. Reads can be processed out of order (backup
     * reader, delayed gateway spool, retries), so an earlier read replaces a later automatic time.
     * Official corrections and manually entered times are never overwritten.
     */
    /**
     * Checkpoints crossed several times (laps, shared start/finish mat). Passes are derived from all of
     * the athlete's reads at the mat: reads are sorted, and a gap longer than the checkpoint's minimum
     * pass interval starts a new pass whose time is its first read. Deriving from every read makes the
     * result independent of the order reads arrive in. Automatic times are updated; official
     * corrections and manual entries keep their pass.
     */
    private void processMultiPass(RfidReadEvent event, RfidReader reader, Participant participant) {
        Checkpoint checkpoint = reader.getCheckpoint();
        UUID raceId = reader.getRace().getId();
        java.util.List<OffsetDateTime> reads = new java.util.ArrayList<>();
        for (RfidReadEvent prior : rfidReadEventRepository.findByMatchedParticipantIdAndCheckpointIdAndStatusInOrderByReadAtAsc(
            participant.getId(), checkpoint.getId(), TIMING_READ_STATUSES)) {
            if (!prior.getId().equals(event.getId()) && prior.getReadAt() != null) {
                reads.add(prior.getReadAt());
            }
        }
        reads.add(event.getReadAt());
        reads.sort(java.util.Comparator.naturalOrder());

        long minGapSeconds = Math.max(checkpoint.getMinPassSeconds() == null ? 60 : checkpoint.getMinPassSeconds(), 1);
        java.util.List<OffsetDateTime> passStarts = new java.util.ArrayList<>();
        OffsetDateTime last = null;
        for (OffsetDateTime read : reads) {
            if (last == null || java.time.Duration.between(last, read).getSeconds() > minGapSeconds) {
                passStarts.add(read);
            }
            last = read;
        }
        if (passStarts.size() > checkpoint.passes()) {
            passStarts = passStarts.subList(0, checkpoint.passes());
        }

        java.util.Map<Integer, CheckpointRecord> byPass = new java.util.HashMap<>();
        for (CheckpointRecord record : checkpointRecordRepository.findByParticipantIdAndCheckpointIdOrderByPassNumberAsc(
            participant.getId(), checkpoint.getId())) {
            byPass.put(com.racetracker.service.CheckpointPasses.pass(record), record);
        }

        CheckpointRecord eventRecord = null;
        boolean changed = false;
        for (int index = 0; index < passStarts.size(); index++) {
            int passNumber = index + 1;
            OffsetDateTime time = passStarts.get(index);
            CheckpointRecord record = byPass.get(passNumber);
            if (record == null) {
                record = new CheckpointRecord();
                record.setRace(reader.getRace());
                record.setCheckpoint(checkpoint);
                record.setParticipant(participant);
                record.setPassNumber(passNumber);
                record.setRecordedAt(time);
                record.setRecordMethod(CheckpointRecord.RecordMethod.RFID);
                record.setIsVerified(true);
                boolean cutoffMissed = passNumber == checkpoint.passes()
                    && evaluateCutoff(checkpoint, participant, raceId, time);
                record.setIsCutoffMissed(cutoffMissed);
                record = checkpointRecordRepository.save(record);
                checkpointProgression.apply(participant, checkpoint, passNumber, cutoffMissed);
                changed = true;
            } else if (record.getRecordMethod() == CheckpointRecord.RecordMethod.RFID
                && record.getAdjustedRecordedAt() == null && !time.equals(record.getRecordedAt())) {
                record.setRecordedAt(time);
                record = checkpointRecordRepository.save(record);
                changed = true;
            }
            if (time.equals(event.getReadAt())) {
                eventRecord = record;
            }
        }

        if (changed) {
            participantRepository.save(participant);
            cutoffReevaluation.reevaluate(participant, raceId);
            liveSummaryCacheInvalidator.evictLastCheckpointAfterCommit(participant.getId(), raceId);
            trackingService.publishTimingUpdate(raceId, participant.getId());
        }
        markReaderProcessed(reader);
        if (eventRecord != null) {
            markEvent(event, RfidReadEvent.RfidReadEventStatus.ACCEPTED, participant, eventRecord, null);
        } else {
            markEvent(event, RfidReadEvent.RfidReadEventStatus.DUPLICATE, participant, null,
                "Read belongs to a pass that is already recorded");
            rfidIngestMetrics.incrementDuplicate();
        }
    }

    private static final java.util.EnumSet<RfidReadEvent.RfidReadEventStatus> TIMING_READ_STATUSES = java.util.EnumSet.of(
        RfidReadEvent.RfidReadEventStatus.ACCEPTED,
        RfidReadEvent.RfidReadEventStatus.DUPLICATE
    );

    private boolean isEarlierAutomaticRead(CheckpointRecord current, RfidReadEvent event) {
        return current.getRecordMethod() == CheckpointRecord.RecordMethod.RFID
            && current.getAdjustedRecordedAt() == null
            && current.getRecordedAt() != null
            && event.getReadAt() != null
            && event.getReadAt().isBefore(current.getRecordedAt());
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
