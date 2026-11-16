package com.racetracker.service.impl;

import com.racetracker.dto.checkpoint.*;
import com.racetracker.exception.ApiException;
import com.racetracker.exception.ConflictException;
import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.mapper.CheckpointMapper;
import com.racetracker.model.Checkpoint;
import com.racetracker.model.CheckpointRecord;
import com.racetracker.model.Participant;
import com.racetracker.model.Race;
import com.racetracker.model.User;
import com.racetracker.repository.CheckpointRecordRepository;
import com.racetracker.repository.CheckpointRepository;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.RaceRepository;
import com.racetracker.repository.UserRepository;
import com.racetracker.security.RaceAccessGuard;
import com.racetracker.service.CheckpointService;
import com.racetracker.service.CheckpointCutoff;
import com.racetracker.service.CheckpointPasses;
import com.racetracker.service.CheckpointProgression;
import com.racetracker.service.LiveSummaryCacheInvalidator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class CheckpointServiceImpl implements CheckpointService {

    private final CheckpointRepository checkpointRepository;
    private final CheckpointRecordRepository checkpointRecordRepository;
    private final ParticipantRepository participantRepository;
    private final RaceRepository raceRepository;
    private final UserRepository userRepository;
    private final CheckpointMapper checkpointMapper;
    private final LiveSummaryCacheInvalidator liveSummaryCacheInvalidator;
    private final RaceAccessGuard raceAccessGuard;
    private final CheckpointProgression checkpointProgression;

    private final com.racetracker.service.CutoffReevaluation cutoffReevaluation;
    private final com.racetracker.service.ResultLock resultLock;
    private final com.racetracker.service.AuditEventService auditEventService;
    private final com.racetracker.repository.CheckpointRecordCorrectionRepository correctionRepository;
    private final com.racetracker.repository.RfidReadEventRepository rfidReadEventRepository;

    public CheckpointServiceImpl(
        CheckpointRepository checkpointRepository,
        CheckpointRecordRepository checkpointRecordRepository,
        ParticipantRepository participantRepository,
        RaceRepository raceRepository,
        UserRepository userRepository,
        CheckpointMapper checkpointMapper,
        LiveSummaryCacheInvalidator liveSummaryCacheInvalidator,
        RaceAccessGuard raceAccessGuard,
        CheckpointProgression checkpointProgression,
        com.racetracker.service.CutoffReevaluation cutoffReevaluation,
        com.racetracker.service.ResultLock resultLock,
        com.racetracker.service.AuditEventService auditEventService,
        com.racetracker.repository.CheckpointRecordCorrectionRepository correctionRepository,
        com.racetracker.repository.RfidReadEventRepository rfidReadEventRepository
    ) {
        this.checkpointRepository = checkpointRepository;
        this.checkpointRecordRepository = checkpointRecordRepository;
        this.participantRepository = participantRepository;
        this.raceRepository = raceRepository;
        this.userRepository = userRepository;
        this.checkpointMapper = checkpointMapper;
        this.liveSummaryCacheInvalidator = liveSummaryCacheInvalidator;
        this.raceAccessGuard = raceAccessGuard;
        this.checkpointProgression = checkpointProgression;
        this.cutoffReevaluation = cutoffReevaluation;
        this.resultLock = resultLock;
        this.auditEventService = auditEventService;
        this.correctionRepository = correctionRepository;
        this.rfidReadEventRepository = rfidReadEventRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CheckpointDto> listByRace(UUID raceId) {
        raceAccessGuard.assertCanRead(raceId);
        verifyRaceExists(raceId);
        return checkpointRepository.findByRaceIdOrderByOrderIndexAsc(raceId)
            .stream()
            .map(checkpointMapper::toDto)
            .toList();
    }

    @Override
    @Transactional
    public CheckpointDto create(UUID raceId, CheckpointCreateRequest request) {
        raceAccessGuard.assertCanManage(raceId);
        Race race = getRace(raceId);
        resultLock.assertOpen(race);

        Checkpoint checkpoint = new Checkpoint();
        checkpoint.setRace(race);
        checkpoint.setName(request.getName().trim());
        checkpoint.setCheckpointType(parseCheckpointType(request.getCheckpointType()));
        checkpoint.setDistanceFromStartKm(request.getDistanceFromStartKm());
        checkpoint.setLatitude(request.getLatitude());
        checkpoint.setLongitude(request.getLongitude());
        checkpoint.setOrderIndex(request.getOrderIndex());
        checkpoint.setCutoffTime(parseDurationNullable(request.getCutoffTime()));
        checkpoint.setHasRfidReader(request.getHasRfidReader() != null ? request.getHasRfidReader() : false);
        checkpoint.setHasQrScanner(request.getHasQrScanner() != null ? request.getHasQrScanner() : false);
        applyPassSettings(checkpoint, request.getExpectedPasses(), request.getMinPassSeconds(), request.getFirstPassIsStart());

        Checkpoint saved = checkpointRepository.save(checkpoint);
        return checkpointMapper.toDto(saved);
    }

    @Override
    @Transactional
    public CheckpointDto update(UUID raceId, UUID checkpointId, CheckpointUpdateRequest request) {
        raceAccessGuard.assertCanManage(raceId);
        Checkpoint checkpoint = getCheckpoint(raceId, checkpointId);
        resultLock.assertOpen(raceId);

        if (request.getName() != null) checkpoint.setName(request.getName());
        if (request.getCheckpointType() != null) checkpoint.setCheckpointType(parseCheckpointType(request.getCheckpointType()));
        if (request.getDistanceFromStartKm() != null) checkpoint.setDistanceFromStartKm(request.getDistanceFromStartKm());
        if (request.getLatitude() != null) checkpoint.setLatitude(request.getLatitude());
        if (request.getLongitude() != null) checkpoint.setLongitude(request.getLongitude());
        if (request.getOrderIndex() != null) checkpoint.setOrderIndex(request.getOrderIndex());
        if (request.getCutoffTime() != null) checkpoint.setCutoffTime(parseDurationNullable(request.getCutoffTime()));
        if (request.getHasRfidReader() != null) checkpoint.setHasRfidReader(request.getHasRfidReader());
        if (request.getHasQrScanner() != null) checkpoint.setHasQrScanner(request.getHasQrScanner());
        applyPassSettings(checkpoint, request.getExpectedPasses(), request.getMinPassSeconds(), request.getFirstPassIsStart());

        liveSummaryCacheInvalidator.evictAllLastCheckpointsAfterCommit();
        Checkpoint saved = checkpointRepository.save(checkpoint);
        return checkpointMapper.toDto(saved);
    }

    @Override
    @Transactional
    public void delete(UUID raceId, UUID checkpointId) {
        raceAccessGuard.assertCanManage(raceId);
        Checkpoint checkpoint = getCheckpoint(raceId, checkpointId);
        resultLock.assertOpen(raceId);
        if (checkpointRecordRepository.existsIncludingVoidedByCheckpointId(checkpointId)
            || rfidReadEventRepository.existsByCheckpointId(checkpointId)) {
            throw new ConflictException(
                "This checkpoint has timing records or RFID reads. It cannot be deleted without destroying timing evidence.",
                "CHECKPOINT_HAS_TIMING_DATA");
        }
        checkpointRepository.delete(checkpoint);
        auditEventService.recordInCurrentTransaction("CHECKPOINT_DELETED", currentUserIdOrNull(), "CHECKPOINT",
            checkpointId.toString(), "SUCCESS", java.util.Map.of("raceId", raceId.toString(), "name", String.valueOf(checkpoint.getName())));
        liveSummaryCacheInvalidator.evictAllLastCheckpointsAfterCommit();
    }

    @Override
    @Transactional
    public CheckpointRecordDto record(UUID raceId, UUID checkpointId, CheckpointRecordRequest request) {
        raceAccessGuard.assertCanAccessCheckpoint(raceId, checkpointId);
        if (request.getRecordedAt() != null) {
            throw new ApiException("Live checkpoint records cannot specify recordedAt",
                "CHECKPOINT_TIMESTAMP_NOT_ALLOWED");
        }
        resultLock.assertOpen(raceId);
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime captured = request.getCapturedOfflineAt();
        if (captured == null) {
            return saveRecord(raceId, checkpointId, request, now, false);
        }
        if (captured.isAfter(now.plus(OFFLINE_CLOCK_ALLOWANCE)) || captured.isBefore(now.minus(OFFLINE_MAX_AGE))) {
            throw new ApiException("Offline captures must be from the last 24 hours and not in the future",
                "OFFLINE_CAPTURE_OUT_OF_RANGE");
        }
        return saveRecord(raceId, checkpointId, request, captured, true);
    }

    private static final java.time.Duration OFFLINE_CLOCK_ALLOWANCE = java.time.Duration.ofMinutes(2);
    private static final java.time.Duration OFFLINE_MAX_AGE = java.time.Duration.ofHours(24);

    @Override
    @Transactional
    public CheckpointRecordDto recordImported(UUID raceId, UUID checkpointId, CheckpointRecordRequest request) {
        raceAccessGuard.assertCanImportTiming(raceId);
        resultLock.assertOpen(raceId);
        return saveRecord(raceId, checkpointId, request,
            request.getRecordedAt() == null ? OffsetDateTime.now() : request.getRecordedAt(), false);
    }

    private CheckpointRecordDto saveRecord(UUID raceId, UUID checkpointId, CheckpointRecordRequest request,
                                           OffsetDateTime recordedAt, boolean capturedOffline) {
        Checkpoint checkpoint = getCheckpoint(raceId, checkpointId);
        Participant participant = resolveParticipant(raceId, request);
        OffsetDateTime gun = CheckpointCutoff.gunStart(participant);
        if (capturedOffline && gun != null && recordedAt.isBefore(gun)) {
            throw new ConflictException("This capture is earlier than the athlete's start", "OFFLINE_CAPTURE_BEFORE_GUN");
        }
        if (!CheckpointProgression.isTimeableEntrant(participant.getStatus())) {
            throw new ConflictException("Participant is not an accepted entrant for this race", "PARTICIPANT_NOT_ENTERED");
        }

        int passNumber = 1;
        if (checkpoint.passes() > 1) {
            int recorded = checkpointRecordRepository
                .findByParticipantIdAndCheckpointIdOrderByPassNumberAsc(participant.getId(), checkpoint.getId()).size();
            if (recorded >= checkpoint.passes()) {
                throw new ConflictException("Every expected pass is already recorded for this participant",
                    "CHECKPOINT_ALREADY_RECORDED");
            }
            passNumber = recorded + 1;
        } else if (checkpointRecordRepository.findFirstByParticipantIdAndCheckpointIdOrderByPassNumberAsc(participant.getId(), checkpoint.getId()).isPresent()) {
            throw new ConflictException("Checkpoint already recorded for this participant", "CHECKPOINT_ALREADY_RECORDED");
        }

        CheckpointRecord record = new CheckpointRecord();
        record.setRace(checkpoint.getRace());
        record.setCheckpoint(checkpoint);
        record.setParticipant(participant);
        record.setRecordedAt(recordedAt);
        record.setRecordMethod(parseRecordMethod(request.getMethod()));
        record.setIsVerified(true);
        record.setPassNumber(passNumber);
        record.setRecordedBy(resolveCurrentUserOrNull());
        if (capturedOffline) {
            record.setNotes("Captured offline on a staff device");
        }

        boolean cutoffMissed = evaluateCutoff(checkpoint, participant, raceId, recordedAt);
        record.setIsCutoffMissed(cutoffMissed);

        CheckpointRecord saved = checkpointRecordRepository.save(record);
        checkpointProgression.apply(participant, checkpoint, passNumber, cutoffMissed);
        participantRepository.save(participant);
        liveSummaryCacheInvalidator.evictLastCheckpointAfterCommit(participant.getId(), raceId);
        if (capturedOffline) {
            auditEventService.recordInCurrentTransaction("CHECKPOINT_RECORDED_OFFLINE", currentUserIdOrNull(),
                "CHECKPOINT_RECORD", saved.getId().toString(), "SUCCESS",
                java.util.Map.of("raceId", raceId.toString(), "checkpointId", checkpointId.toString(),
                    "capturedAt", recordedAt.toString()));
        }
        return checkpointMapper.toRecordDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CheckpointRecordDto> listRecords(UUID raceId, UUID checkpointId) {
        return listRecords(raceId, checkpointId, false);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CheckpointRecordDto> listRecords(UUID raceId, UUID checkpointId, boolean includeVoided) {
        raceAccessGuard.assertCanAccessCheckpoint(raceId, checkpointId);
        getCheckpoint(raceId, checkpointId);
        List<CheckpointRecordDto> active = checkpointRecordRepository
            .findByRaceIdAndCheckpointIdOrderByRecordedAtAsc(raceId, checkpointId).stream()
            .map(checkpointMapper::toRecordDto)
            .toList();
        if (!includeVoided) {
            return active;
        }
        raceAccessGuard.assertCanManage(raceId);
        List<CheckpointRecordDto> all = new java.util.ArrayList<>(active);
        checkpointRecordRepository.findVoidedByCheckpoint(raceId, checkpointId).stream()
            .map(checkpointMapper::toRecordDto)
            .forEach(all::add);
        return all;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CheckpointRecordDto> listRecentRecords(UUID raceId, UUID checkpointId, int limit) {
        raceAccessGuard.assertCanAccessCheckpoint(raceId, checkpointId);
        getCheckpoint(raceId, checkpointId);
        int size = Math.max(1, Math.min(limit, MAX_RECENT_RECORDS));
        return checkpointRecordRepository.findByRaceIdAndCheckpointIdOrderByCreatedAtDesc(raceId, checkpointId,
                org.springframework.data.domain.PageRequest.of(0, size)).stream()
            .map(checkpointMapper::toRecordDto)
            .toList();
    }

    private static final int MAX_RECENT_RECORDS = 100;
    static final java.time.Duration STAFF_UNDO_WINDOW = java.time.Duration.ofMinutes(5);

    @Override
    @Transactional
    public CheckpointRecordDto voidRecord(UUID raceId, UUID checkpointId, UUID recordId, CheckpointRecordVoidRequest request) {
        boolean manager = raceAccessGuard.canManage(raceId);
        if (!manager) {
            raceAccessGuard.assertCanAccessCheckpoint(raceId, checkpointId);
        }
        Checkpoint checkpoint = getCheckpoint(raceId, checkpointId);
        resultLock.assertOpen(raceId);
        String reason = requireReason(request.getReason());
        CheckpointRecord record = checkpointRecordRepository.findByRaceIdAndCheckpointIdAndId(raceId, checkpointId, recordId)
            .orElseThrow(() -> new ResourceNotFoundException("Checkpoint record not found"));
        if (!manager) {
            assertOwnRecentEntry(record);
        }
        UUID participantId = record.getParticipant().getId();
        boolean missedCutoff = Boolean.TRUE.equals(record.getIsCutoffMissed());
        boolean wasFinish = CheckpointPasses.isFinish(record);
        User actor = resolveCurrentUserOrNull();
        OffsetDateTime now = OffsetDateTime.now();
        auditTimingAction("CHECKPOINT_RECORD_VOIDED", actor, record, raceId, checkpointId, reason, null);
        voidInPlace(record, checkpoint, reason, actor, now, "VOIDED");
        refreshParticipantAfterRemoval(raceId, participantId, missedCutoff, wasFinish);
        return checkpointMapper.toRecordDto(checkpointRecordRepository.findVoided(raceId, checkpointId, recordId)
            .orElseThrow(() -> new ResourceNotFoundException("Checkpoint record not found")));
    }

    @Override
    @Transactional
    public CheckpointRecordDto restoreRecord(UUID raceId, UUID checkpointId, UUID recordId, CheckpointRecordVoidRequest request) {
        raceAccessGuard.assertCanManage(raceId);
        Checkpoint checkpoint = getCheckpoint(raceId, checkpointId);
        resultLock.assertOpen(raceId);
        String reason = requireReason(request.getReason());
        CheckpointRecord record = checkpointRecordRepository.findVoided(raceId, checkpointId, recordId)
            .orElseThrow(() -> new ResourceNotFoundException("Voided checkpoint record not found"));
        UUID participantId = record.getParticipant().getId();
        long active = checkpointRecordRepository.countByParticipantIdAndCheckpointId(participantId, checkpointId);
        if (active >= checkpoint.passes()) {
            throw new ConflictException("This athlete already has a time at this checkpoint; void it before restoring",
                "TIMING_SLOT_OCCUPIED");
        }
        User actor = resolveCurrentUserOrNull();
        OffsetDateTime now = OffsetDateTime.now();
        writeHistory(record, raceId, checkpointId, "RESTORED", reason, actor, now);
        auditTimingAction("CHECKPOINT_RECORD_RESTORED", actor, record, raceId, checkpointId, reason, null);
        if (checkpoint.passes() > 1) {
            checkpointRecordRepository.parkActivePasses(participantId, checkpointId);
            checkpointRecordRepository.restore(recordId, 200001);
            checkpointRecordRepository.renumberActivePasses(participantId, checkpointId);
        } else {
            checkpointRecordRepository.restore(recordId, 1);
        }
        rfidReadEventRepository.restoreForCheckpointRecord(recordId);
        CheckpointRecord restored = checkpointRecordRepository.findByRaceIdAndCheckpointIdAndId(raceId, checkpointId, recordId)
            .orElseThrow(() -> new ResourceNotFoundException("Checkpoint record not found"));
        Participant participant = restored.getParticipant();
        applySilentProgression(participant, restored);
        reevaluateCutoffs(participant, raceId);
        liveSummaryCacheInvalidator.evictLastCheckpointAfterCommit(participantId, raceId);
        return checkpointMapper.toRecordDto(restored);
    }

    @Override
    @Transactional
    public CheckpointRecordDto reassignRecord(UUID raceId, UUID checkpointId, UUID recordId,
                                              CheckpointRecordReassignRequest request) {
        raceAccessGuard.assertCanManage(raceId);
        Checkpoint checkpoint = getCheckpoint(raceId, checkpointId);
        resultLock.assertOpen(raceId);
        String reason = requireReason(request.getReason());
        CheckpointRecord original = checkpointRecordRepository.findByRaceIdAndCheckpointIdAndId(raceId, checkpointId, recordId)
            .orElseThrow(() -> new ResourceNotFoundException("Checkpoint record not found"));
        CheckpointRecordRequest target = new CheckpointRecordRequest();
        target.setParticipantId(request.getParticipantId());
        target.setParticipantBibNumber(request.getParticipantBibNumber());
        Participant targetParticipant = resolveParticipant(raceId, target);
        Participant source = original.getParticipant();
        if (targetParticipant.getId().equals(source.getId())) {
            throw new ApiException("The record already belongs to this athlete", "REASSIGN_SAME_PARTICIPANT");
        }
        if (!CheckpointProgression.isTimeableEntrant(targetParticipant.getStatus())) {
            throw new ConflictException("Participant is not an accepted entrant for this race", "PARTICIPANT_NOT_ENTERED");
        }
        if (checkpointRecordRepository.countByParticipantIdAndCheckpointId(targetParticipant.getId(), checkpointId)
            >= checkpoint.passes()) {
            throw new ConflictException("That athlete already has every expected time at this checkpoint",
                "TIMING_SLOT_OCCUPIED");
        }

        UUID sourceId = source.getId();
        UUID targetId = targetParticipant.getId();
        User actor = resolveCurrentUserOrNull();
        OffsetDateTime now = OffsetDateTime.now();
        boolean sourceMissed = Boolean.TRUE.equals(original.getIsCutoffMissed());
        boolean sourceFinish = CheckpointPasses.isFinish(original);

        CheckpointRecord moved = new CheckpointRecord();
        moved.setRace(original.getRace());
        moved.setCheckpoint(original.getCheckpoint());
        moved.setParticipant(targetParticipant);
        moved.setRecordedAt(original.getRecordedAt());
        moved.setAdjustedRecordedAt(original.getAdjustedRecordedAt());
        moved.setAdjustmentReason(original.getAdjustmentReason());
        moved.setAdjustedBy(original.getAdjustedBy());
        moved.setAdjustedAt(original.getAdjustedAt());
        moved.setRecordMethod(original.getRecordMethod());
        moved.setRecordedBy(original.getRecordedBy());
        moved.setIsVerified(original.getIsVerified());
        moved.setIsCutoffMissed(false);
        moved.setPassNumber(checkpoint.passes() > 1 ? 200001 : 1);
        moved.setNotes("Reassigned from bib " + (source.getBibNumber() == null ? "-" : source.getBibNumber()));

        auditTimingAction("CHECKPOINT_RECORD_REASSIGNED", actor, original, raceId, checkpointId, reason, targetId);
        voidInPlace(original, checkpoint, reason, actor, now, "REASSIGNED");
        CheckpointRecord saved = checkpointRecordRepository.saveAndFlush(moved);
        rfidReadEventRepository.reassignCheckpointRecord(recordId, saved.getId(), targetId,
            "Reassigned from another athlete's record");
        UUID savedId = saved.getId();
        if (checkpoint.passes() > 1) {
            checkpointRecordRepository.parkActivePasses(targetId, checkpointId);
            checkpointRecordRepository.renumberActivePasses(targetId, checkpointId);
        }

        writeHistory(saved, raceId, checkpointId, "REASSIGNED", reason, actor, now);
        refreshParticipantAfterRemoval(raceId, sourceId, sourceMissed, sourceFinish);

        CheckpointRecord result = checkpointRecordRepository.findByRaceIdAndCheckpointIdAndId(raceId, checkpointId, savedId)
            .orElseThrow(() -> new ResourceNotFoundException("Checkpoint record not found"));
        Participant reassignedTo = result.getParticipant();
        applySilentProgression(reassignedTo, result);
        reevaluateCutoffs(reassignedTo, raceId);
        liveSummaryCacheInvalidator.evictLastCheckpointAfterCommit(targetId, raceId);
        return checkpointMapper.toRecordDto(result);
    }

    private void assertOwnRecentEntry(CheckpointRecord record) {
        UUID actorId = currentUserIdOrNull();
        boolean own = actorId != null && record.getRecordedBy() != null && actorId.equals(record.getRecordedBy().getId());
        if (!own || record.getRecordMethod() == CheckpointRecord.RecordMethod.RFID) {
            throw new org.springframework.security.access.AccessDeniedException("Staff can only undo their own entries");
        }
        OffsetDateTime entered = record.getCreatedAt() != null ? record.getCreatedAt() : record.getRecordedAt();
        if (entered == null || entered.isBefore(OffsetDateTime.now().minus(STAFF_UNDO_WINDOW))) {
            throw new ConflictException("Entries can only be undone within 5 minutes; ask a race admin to correct it",
                "RECORD_UNDO_WINDOW_EXPIRED");
        }
    }

    private void voidInPlace(CheckpointRecord record, Checkpoint checkpoint, String reason, User actor,
                             OffsetDateTime now, String action) {
        UUID participantId = record.getParticipant().getId();
        writeHistory(record, record.getRace().getId(), checkpoint.getId(), action, reason, actor, now);
        record.setVoidedAt(now);
        record.setVoidedBy(actor != null ? actor.getId() : null);
        record.setVoidReason(reason);
        checkpointRecordRepository.saveAndFlush(record);
        if (!"REASSIGNED".equals(action)) {
            rfidReadEventRepository.voidForCheckpointRecord(record.getId(), "Timing record voided by an official");
        }
        if (checkpoint.passes() > 1) {
            checkpointRecordRepository.parkActivePasses(participantId, checkpoint.getId());
            checkpointRecordRepository.renumberActivePasses(participantId, checkpoint.getId());
        }
    }

    private void refreshParticipantAfterRemoval(UUID raceId, UUID participantId, boolean missedCutoff, boolean wasFinish) {
        Participant participant = participantRepository.findByRaceIdAndId(raceId, participantId)
            .orElseThrow(() -> new ResourceNotFoundException("Participant not found"));
        cutoffReevaluation.reevaluateAfterRemoval(participant, raceId, missedCutoff, wasFinish);
        liveSummaryCacheInvalidator.evictLastCheckpointAfterCommit(participantId, raceId);
    }

    private void applySilentProgression(Participant participant, CheckpointRecord record) {
        Participant.ParticipantStatus next = CheckpointProgression.nextStatus(participant.getStatus(),
            record.getCheckpoint(), CheckpointPasses.pass(record), false);
        if (next != participant.getStatus()) {
            participant.setStatus(next);
            participantRepository.save(participant);
        }
    }

    private void writeHistory(CheckpointRecord record, UUID raceId, UUID checkpointId, String action, String reason,
                              User actor, OffsetDateTime now) {
        com.racetracker.model.CheckpointRecordCorrection history = new com.racetracker.model.CheckpointRecordCorrection();
        history.setCheckpointRecordId(record.getId());
        history.setRaceId(raceId);
        history.setParticipantId(record.getParticipant().getId());
        history.setCheckpointId(checkpointId);
        history.setPreviousEffectiveAt(CheckpointCutoff.effectiveTime(record));
        history.setPreviousVerified(record.getIsVerified());
        history.setNewAdjustedAt(record.getAdjustedRecordedAt());
        history.setNewVerified(record.getIsVerified());
        history.setReason(reason);
        history.setAction(action);
        history.setCorrectedBy(actor != null ? actor.getId() : null);
        history.setCorrectedAt(now);
        correctionRepository.save(history);
    }

    private void auditTimingAction(String action, User actor, CheckpointRecord record, UUID raceId, UUID checkpointId,
                                   String reason, UUID reassignedTo) {
        java.util.Map<String, Object> audit = new java.util.LinkedHashMap<>();
        audit.put("raceId", raceId.toString());
        audit.put("participantId", record.getParticipant().getId().toString());
        audit.put("checkpointId", checkpointId.toString());
        audit.put("time", String.valueOf(CheckpointCutoff.effectiveTime(record)));
        audit.put("reason", reason);
        if (reassignedTo != null) {
            audit.put("reassignedTo", reassignedTo.toString());
        }
        auditEventService.recordInCurrentTransaction(action, actor != null ? actor.getId() : null,
            "CHECKPOINT_RECORD", record.getId().toString(), "SUCCESS", audit);
    }

    private String requireReason(String raw) {
        String reason = raw == null ? "" : raw.trim();
        if (reason.length() < 5 || reason.length() > 500) {
            throw new ApiException("A reason of 5 to 500 characters is required", "CORRECTION_REASON_REQUIRED");
        }
        return reason;
    }

    @Override
    @Transactional
    public CheckpointRecordDto correctRecord(UUID raceId, UUID checkpointId, UUID recordId, CheckpointRecordCorrectionRequest request) {
        raceAccessGuard.assertCanManage(raceId);
        getCheckpoint(raceId, checkpointId);
        CheckpointRecord record = checkpointRecordRepository.findByRaceIdAndCheckpointIdAndId(raceId, checkpointId, recordId)
            .orElseThrow(() -> new ResourceNotFoundException("Checkpoint record not found"));
        resultLock.assertOpen(raceId);
        String reason = request.getReason() == null ? "" : request.getReason().trim();
        if (reason.length() < 5) {
            throw new ApiException("A reason of at least 5 characters is required for every time correction",
                "CORRECTION_REASON_REQUIRED");
        }
        if (request.getAdjustedRecordedAt() == null && request.getVerified() == null) {
            throw new ApiException("A correction must change the time or the verification state", "CORRECTION_EMPTY");
        }

        User actor = resolveCurrentUserOrNull();
        OffsetDateTime now = OffsetDateTime.now();
        com.racetracker.model.CheckpointRecordCorrection history = new com.racetracker.model.CheckpointRecordCorrection();
        history.setCheckpointRecordId(record.getId());
        history.setRaceId(raceId);
        history.setParticipantId(record.getParticipant().getId());
        history.setCheckpointId(checkpointId);
        history.setPreviousEffectiveAt(CheckpointCutoff.effectiveTime(record));
        history.setPreviousVerified(record.getIsVerified());
        history.setNewAdjustedAt(request.getAdjustedRecordedAt() != null ? request.getAdjustedRecordedAt() : record.getAdjustedRecordedAt());
        history.setNewVerified(request.getVerified() != null ? request.getVerified() : record.getIsVerified());
        history.setReason(reason);
        history.setCorrectedBy(actor != null ? actor.getId() : null);
        history.setCorrectedAt(now);
        correctionRepository.save(history);

        if (request.getAdjustedRecordedAt() != null) {
            record.setAdjustedRecordedAt(request.getAdjustedRecordedAt());
        }
        if (request.getVerified() != null) {
            record.setIsVerified(request.getVerified());
        }
        record.setAdjustmentReason(reason);
        record.setAdjustedBy(actor != null ? actor.getId() : null);
        record.setAdjustedAt(now);

        CheckpointRecord saved = checkpointRecordRepository.save(record);
        java.util.Map<String, Object> audit = new java.util.LinkedHashMap<>();
        audit.put("raceId", raceId.toString());
        audit.put("participantId", record.getParticipant().getId().toString());
        audit.put("checkpointId", checkpointId.toString());
        audit.put("previous", String.valueOf(history.getPreviousEffectiveAt()));
        audit.put("adjustedTo", String.valueOf(history.getNewAdjustedAt()));
        audit.put("verified", String.valueOf(history.getNewVerified()));
        audit.put("reason", reason);
        auditEventService.recordInCurrentTransaction("CHECKPOINT_TIME_CORRECTED", actor != null ? actor.getId() : null,
            "CHECKPOINT_RECORD", record.getId().toString(), "SUCCESS", audit);
        reevaluateCutoffs(saved.getParticipant(), raceId);
        liveSummaryCacheInvalidator.evictLastCheckpointAfterCommit(saved.getParticipant().getId(), raceId);
        return checkpointMapper.toRecordDto(saved);
    }

    private void reevaluateCutoffs(Participant participant, UUID raceId) {
        cutoffReevaluation.reevaluate(participant, raceId);
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

    private Participant resolveParticipant(UUID raceId, CheckpointRecordRequest request) {
        if (request.getParticipantId() != null) {
            return participantRepository.findByRaceIdAndId(raceId, request.getParticipantId())
                .orElseThrow(() -> new ResourceNotFoundException("Participant not found"));
        }

        if (request.getParticipantBibNumber() != null && !request.getParticipantBibNumber().isBlank()) {
            return participantRepository.findByRaceIdAndBibNumber(raceId, request.getParticipantBibNumber().trim())
                .orElseThrow(() -> new ResourceNotFoundException("Participant not found"));
        }

        throw new ApiException("participantId or participantBibNumber is required");
    }

    private CheckpointRecord.RecordMethod parseRecordMethod(String input) {
        if (input == null || input.isBlank()) {
            return CheckpointRecord.RecordMethod.MANUAL;
        }
        try {
            return CheckpointRecord.RecordMethod.valueOf(input.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ApiException("Invalid record method: " + input);
        }
    }

    private Checkpoint.CheckpointType parseCheckpointType(String input) {
        if (input == null || input.isBlank()) {
            throw new ApiException("Checkpoint type is required");
        }
        try {
            return Checkpoint.CheckpointType.valueOf(input.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ApiException("Invalid checkpoint type: " + input);
        }
    }

    private Duration parseDurationNullable(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }

        String[] parts = input.split(":");
        try {
            if (parts.length != 3) {
                throw new ApiException("Duration must be HH:mm:ss");
            }
            long hours = Long.parseLong(parts[0]);
            long minutes = Long.parseLong(parts[1]);
            long seconds = Long.parseLong(parts[2]);
            return Duration.ofHours(hours).plusMinutes(minutes).plusSeconds(seconds);
        } catch (NumberFormatException ex) {
            throw new ApiException("Duration must be HH:mm:ss");
        }
    }

    private Checkpoint getCheckpoint(UUID raceId, UUID checkpointId) {
        return checkpointRepository.findByRaceIdAndId(raceId, checkpointId)
            .orElseThrow(() -> new ResourceNotFoundException("Checkpoint not found"));
    }

    private Race getRace(UUID raceId) {
        return raceRepository.findById(raceId)
            .orElseThrow(() -> new ResourceNotFoundException("Race not found"));
    }

    private void verifyRaceExists(UUID raceId) {
        if (!raceRepository.existsById(raceId)) {
            throw new ResourceNotFoundException("Race not found");
        }
    }

    private User resolveCurrentUserOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        return userRepository.findByEmailIgnoreCase(authentication.getName()).orElse(null);
    }

    private UUID currentUserIdOrNull() {
        User actor = resolveCurrentUserOrNull();
        return actor == null ? null : actor.getId();
    }

    private void applyPassSettings(Checkpoint checkpoint, Integer expectedPasses, Integer minPassSeconds, Boolean firstPassIsStart) {
        if (expectedPasses != null) {
            if (expectedPasses < 1 || expectedPasses > 200) {
                throw new ApiException("Expected passes must be between 1 and 200", "INVALID_EXPECTED_PASSES");
            }
            checkpoint.setExpectedPasses(expectedPasses);
        }
        if (minPassSeconds != null) {
            if (minPassSeconds < 1) {
                throw new ApiException("The minimum time between passes must be at least 1 second", "INVALID_MIN_PASS_SECONDS");
            }
            checkpoint.setMinPassSeconds(minPassSeconds);
        }
        if (firstPassIsStart != null) {
            if (firstPassIsStart && checkpoint.getCheckpointType() != Checkpoint.CheckpointType.FINISH) {
                throw new ApiException("Only a finish checkpoint can use its first pass as the start", "INVALID_FIRST_PASS_START");
            }
            checkpoint.setFirstPassIsStart(firstPassIsStart);
        }
    }
}
