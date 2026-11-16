package com.racetracker.service.impl;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.racetracker.dto.tracking.SelfScanClaimDto;
import com.racetracker.dto.tracking.SelfScanDecisionRequest;
import com.racetracker.exception.ApiException;
import com.racetracker.exception.ConflictException;
import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.exception.ServiceUnavailableException;
import com.racetracker.model.Checkpoint;
import com.racetracker.model.CheckpointRecord;
import com.racetracker.model.Participant;
import com.racetracker.model.SelfScanClaim;
import com.racetracker.model.User;
import com.racetracker.repository.CheckpointRecordRepository;
import com.racetracker.repository.CheckpointRepository;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.SelfScanClaimRepository;
import com.racetracker.repository.UserRepository;
import com.racetracker.security.RaceAccessGuard;
import com.racetracker.service.AuditEventService;
import com.racetracker.service.CheckpointCutoff;
import com.racetracker.service.CheckpointProgression;
import com.racetracker.service.LiveSummaryCacheInvalidator;
import com.racetracker.service.SelfScanService;
import com.racetracker.service.SelfScanTokens;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SelfScanServiceImpl implements SelfScanService {

    private static final int QR_SIZE = 600;
    private static final int MAX_OPEN_CLAIMS = 200;

    private final CheckpointRepository checkpointRepository;
    private final CheckpointRecordRepository checkpointRecordRepository;
    private final SelfScanClaimRepository selfScanClaimRepository;
    private final ParticipantRepository participantRepository;
    private final UserRepository userRepository;
    private final RaceAccessGuard raceAccessGuard;
    private final CheckpointProgression checkpointProgression;
    private final LiveSummaryCacheInvalidator liveSummaryCacheInvalidator;
    private final AuditEventService auditEventService;
    private final com.racetracker.service.ResultLock resultLock;

    public SelfScanServiceImpl(
        CheckpointRepository checkpointRepository,
        CheckpointRecordRepository checkpointRecordRepository,
        SelfScanClaimRepository selfScanClaimRepository,
        ParticipantRepository participantRepository,
        UserRepository userRepository,
        RaceAccessGuard raceAccessGuard,
        CheckpointProgression checkpointProgression,
        LiveSummaryCacheInvalidator liveSummaryCacheInvalidator,
        AuditEventService auditEventService,
        com.racetracker.service.ResultLock resultLock
    ) {
        this.checkpointRepository = checkpointRepository;
        this.checkpointRecordRepository = checkpointRecordRepository;
        this.selfScanClaimRepository = selfScanClaimRepository;
        this.participantRepository = participantRepository;
        this.userRepository = userRepository;
        this.raceAccessGuard = raceAccessGuard;
        this.checkpointProgression = checkpointProgression;
        this.liveSummaryCacheInvalidator = liveSummaryCacheInvalidator;
        this.auditEventService = auditEventService;
        this.resultLock = resultLock;
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] selfScanQrPng(UUID raceId, UUID checkpointId) {
        raceAccessGuard.assertCanPerformOperationalAction(raceId);
        Checkpoint checkpoint = requireSelfScanCheckpoint(raceId, checkpointId);
        String payload = SelfScanTokens.qrPayload(checkpoint.getId(), raceId, checkpoint.getSelfScanToken());
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            BitMatrix matrix = new QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE,
                Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 2));
            MatrixToImageWriter.writeToStream(matrix, "PNG", output);
            return output.toByteArray();
        } catch (com.google.zxing.WriterException | IOException ex) {
            throw new ServiceUnavailableException("Self-scan code could not be generated", "SELF_SCAN_CODE_UNAVAILABLE");
        }
    }

    @Override
    @Transactional
    public void rotateSelfScanCode(UUID raceId, UUID checkpointId) {
        raceAccessGuard.assertCanManage(raceId);
        Checkpoint checkpoint = requireSelfScanCheckpoint(raceId, checkpointId);
        checkpoint.setSelfScanToken(SelfScanTokens.generate());
        checkpointRepository.save(checkpoint);
        auditEventService.recordInCurrentTransaction("CHECKPOINT_SELF_SCAN_CODE_ROTATED", currentUser().getId(),
            "CHECKPOINT", checkpointId.toString(), "SUCCESS", Map.of("raceId", raceId.toString()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<SelfScanClaimDto> openClaims(UUID raceId) {
        raceAccessGuard.assertCanPerformOperationalAction(raceId);
        return selfScanClaimRepository.findOpenByRaceId(raceId, PageRequest.of(0, MAX_OPEN_CLAIMS)).stream()
            .map(this::toDto)
            .toList();
    }

    @Override
    @Transactional
    public SelfScanClaimDto decide(UUID raceId, UUID claimId, SelfScanDecisionRequest request) {
        raceAccessGuard.assertCanPerformOperationalAction(raceId);
        SelfScanClaim claim = selfScanClaimRepository.findByIdAndRaceIdForUpdate(claimId, raceId)
            .orElseThrow(() -> new ResourceNotFoundException("Self-scan claim not found"));
        resultLock.assertOpen(claim.getRace());
        if (claim.getStatus() != SelfScanClaim.Status.PENDING) {
            throw new ConflictException("This self-scan has already been reviewed", "SELF_SCAN_CLAIM_ALREADY_REVIEWED");
        }
        User reviewer = currentUser();
        Participant participant = claim.getParticipant();
        Checkpoint checkpoint = claim.getCheckpoint();
        OffsetDateTime now = OffsetDateTime.now();
        claim.setReviewedBy(reviewer);
        claim.setReviewedAt(now);
        claim.setReviewNote(request.getNote() == null ? null : request.getNote().trim());

        boolean alreadyTimed = checkpointRecordRepository
            .findFirstByParticipantIdAndCheckpointIdOrderByPassNumberAsc(participant.getId(), checkpoint.getId()).isPresent();
        if (alreadyTimed) {
            claim.setStatus(SelfScanClaim.Status.SUPERSEDED);
        } else if (request.getDecision() == SelfScanDecisionRequest.Decision.APPROVE) {
            if (!CheckpointProgression.isTimeableEntrant(participant.getStatus())) {
                throw new ConflictException("Participant is not an accepted entrant for this race", "PARTICIPANT_NOT_ENTERED");
            }
            recordApprovedCrossing(claim, reviewer);
            claim.setStatus(SelfScanClaim.Status.APPROVED);
        } else {
            claim.setStatus(SelfScanClaim.Status.REJECTED);
        }
        selfScanClaimRepository.save(claim);
        auditEventService.recordInCurrentTransaction("SELF_SCAN_CLAIM_" + claim.getStatus().name(), reviewer.getId(),
            "SELF_SCAN_CLAIM", claimId.toString(), "SUCCESS",
            Map.of("raceId", raceId.toString(), "checkpointId", checkpoint.getId().toString(),
                "participantId", participant.getId().toString()));
        return toDto(claim);
    }

    private void recordApprovedCrossing(SelfScanClaim claim, User reviewer) {
        Participant participant = claim.getParticipant();
        Checkpoint checkpoint = claim.getCheckpoint();
        UUID raceId = claim.getRace().getId();
        List<CheckpointRecord> existing = checkpointRecordRepository
            .findByParticipantIdAndRaceIdOrderByRecordedAtAsc(participant.getId(), raceId);
        boolean cutoffMissed = checkpoint.getCutoffTime() != null && CheckpointCutoff.isMissed(
            checkpoint.getCutoffTime(), CheckpointCutoff.resolveStart(participant, existing), claim.getClaimedAt());

        CheckpointRecord record = new CheckpointRecord();
        record.setRace(claim.getRace());
        record.setCheckpoint(checkpoint);
        record.setParticipant(participant);
        record.setRecordedAt(claim.getClaimedAt());
        record.setRecordMethod(CheckpointRecord.RecordMethod.QR_CODE);
        record.setRecordedBy(reviewer);
        record.setIsVerified(true);
        record.setIsCutoffMissed(cutoffMissed);
        record.setNotes("Self-scan approved by an official");
        checkpointRecordRepository.save(record);

        checkpointProgression.apply(participant, checkpoint, cutoffMissed);
        participantRepository.save(participant);
        liveSummaryCacheInvalidator.evictLastCheckpointAfterCommit(participant.getId(), raceId);
    }

    private Checkpoint requireSelfScanCheckpoint(UUID raceId, UUID checkpointId) {
        Checkpoint checkpoint = checkpointRepository.findByRaceIdAndId(raceId, checkpointId)
            .orElseThrow(() -> new ResourceNotFoundException("Checkpoint not found"));
        if (!checkpoint.acceptsSelfScan()) {
            throw new ApiException("Start and finish checkpoints do not accept athlete self-scans",
                "SELF_SCAN_NOT_ALLOWED");
        }
        return checkpoint;
    }

    private User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new org.springframework.security.access.AccessDeniedException("Authentication required");
        }
        return userRepository.findByEmailIgnoreCase(authentication.getName())
            .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Authentication required"));
    }

    private SelfScanClaimDto toDto(SelfScanClaim claim) {
        Participant participant = claim.getParticipant();
        Checkpoint checkpoint = claim.getCheckpoint();
        return new SelfScanClaimDto(
            claim.getId(),
            participant.getId(),
            participant.getFullName(),
            participant.getBibNumber(),
            checkpoint.getId(),
            checkpoint.getName(),
            claim.getClaimedAt().toString(),
            claim.getReason().name(),
            claim.getDistanceMeters(),
            claim.getFixAgeSeconds(),
            claim.getStatus().name()
        );
    }
}
