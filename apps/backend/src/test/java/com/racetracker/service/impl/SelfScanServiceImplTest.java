package com.racetracker.service.impl;

import com.racetracker.dto.tracking.SelfScanClaimDto;
import com.racetracker.dto.tracking.SelfScanDecisionRequest;
import com.racetracker.exception.ApiException;
import com.racetracker.exception.ConflictException;
import com.racetracker.model.Checkpoint;
import com.racetracker.model.CheckpointRecord;
import com.racetracker.model.Participant;
import com.racetracker.model.Race;
import com.racetracker.model.SelfScanClaim;
import com.racetracker.model.User;
import com.racetracker.repository.CheckpointRecordRepository;
import com.racetracker.repository.CheckpointRepository;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.SelfScanClaimRepository;
import com.racetracker.repository.UserRepository;
import com.racetracker.security.RaceAccessGuard;
import com.racetracker.service.AuditEventService;
import com.racetracker.service.CheckpointProgression;
import com.racetracker.service.LiveSummaryCacheInvalidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SelfScanServiceImplTest {

    private final CheckpointRepository checkpointRepository = mock(CheckpointRepository.class);
    private final CheckpointRecordRepository recordRepository = mock(CheckpointRecordRepository.class);
    private final SelfScanClaimRepository claimRepository = mock(SelfScanClaimRepository.class);
    private final RaceAccessGuard guard = mock(RaceAccessGuard.class);
    private final CheckpointProgression progression = mock(CheckpointProgression.class);
    private final AuditEventService audit = mock(AuditEventService.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private SelfScanServiceImpl service;

    private final UUID raceId = UUID.randomUUID();
    private SelfScanClaim claim;
    private Participant participant;
    private Checkpoint checkpoint;

    @BeforeEach
    void setUp() {
        service = new SelfScanServiceImpl(checkpointRepository, recordRepository, claimRepository,
            mock(ParticipantRepository.class), userRepository, guard, progression,
            mock(LiveSummaryCacheInvalidator.class), audit, org.mockito.Mockito.mock(com.racetracker.service.ResultLock.class));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("official@example.com", null, List.of()));
        when(userRepository.findByEmailIgnoreCase("official@example.com"))
            .thenReturn(Optional.of(User.builder().id(UUID.randomUUID()).email("official@example.com").build()));

        Race race = Race.builder().id(raceId).status(Race.RaceStatus.IN_PROGRESS).build();
        participant = Participant.builder().id(UUID.randomUUID()).race(race).firstName("Tari").lastName("Moyo")
            .bibNumber("101").status(Participant.ParticipantStatus.STARTED).build();
        checkpoint = Checkpoint.builder().id(UUID.randomUUID()).race(race).name("Water 2")
            .checkpointType(Checkpoint.CheckpointType.INTERMEDIATE).selfScanToken("t").build();
        claim = new SelfScanClaim();
        claim.setId(UUID.randomUUID());
        claim.setRace(race);
        claim.setParticipant(participant);
        claim.setCheckpoint(checkpoint);
        claim.setClaimedAt(OffsetDateTime.now().minusMinutes(3));
        claim.setStatus(SelfScanClaim.Status.PENDING);
        claim.setReason(SelfScanClaim.Reason.OUT_OF_RANGE);
        when(claimRepository.findByIdAndRaceIdForUpdate(claim.getId(), raceId)).thenReturn(Optional.of(claim));
        when(recordRepository.findFirstByParticipantIdAndCheckpointIdOrderByPassNumberAsc(participant.getId(), checkpoint.getId()))
            .thenReturn(Optional.empty());
        when(recordRepository.findByParticipantIdAndRaceIdOrderByRecordedAtAsc(any(), any())).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private SelfScanDecisionRequest decision(SelfScanDecisionRequest.Decision value) {
        SelfScanDecisionRequest request = new SelfScanDecisionRequest();
        request.setDecision(value);
        return request;
    }

    @Test
    void approvalRecordsTheCrossingAtTheScanTimeAndAppliesProgression() {
        SelfScanClaimDto result = service.decide(raceId, claim.getId(), decision(SelfScanDecisionRequest.Decision.APPROVE));

        assertThat(result.status()).isEqualTo("APPROVED");
        ArgumentCaptor<CheckpointRecord> record = ArgumentCaptor.forClass(CheckpointRecord.class);
        verify(recordRepository).save(record.capture());
        assertThat(record.getValue().getRecordedAt()).isEqualTo(claim.getClaimedAt());
        assertThat(record.getValue().getIsVerified()).isTrue();
        verify(progression).apply(eq(participant), eq(checkpoint), eq(false));
        verify(guard).assertCanPerformOperationalAction(raceId);
        verify(audit).recordInCurrentTransaction(eq("SELF_SCAN_CLAIM_APPROVED"), any(), eq("SELF_SCAN_CLAIM"),
            eq(claim.getId().toString()), eq("SUCCESS"), any());
    }

    @Test
    void rejectionChangesNothingOfficial() {
        assertThat(service.decide(raceId, claim.getId(), decision(SelfScanDecisionRequest.Decision.REJECT)).status())
            .isEqualTo("REJECTED");
        verify(recordRepository, never()).save(any());
        verify(progression, never()).apply(any(), any(), anyBoolean());
    }

    @Test
    void officialTimingRecordedMeanwhileSupersedesTheClaim() {
        when(recordRepository.findFirstByParticipantIdAndCheckpointIdOrderByPassNumberAsc(participant.getId(), checkpoint.getId()))
            .thenReturn(Optional.of(new CheckpointRecord()));

        assertThat(service.decide(raceId, claim.getId(), decision(SelfScanDecisionRequest.Decision.APPROVE)).status())
            .isEqualTo("SUPERSEDED");
        verify(recordRepository, never()).save(any());
    }

    @Test
    void aClaimCanOnlyBeReviewedOnce() {
        claim.setStatus(SelfScanClaim.Status.REJECTED);

        assertThatThrownBy(() -> service.decide(raceId, claim.getId(), decision(SelfScanDecisionRequest.Decision.APPROVE)))
            .isInstanceOf(ConflictException.class)
            .extracting("code").isEqualTo("SELF_SCAN_CLAIM_ALREADY_REVIEWED");
    }

    @Test
    void finishCheckpointHasNoSelfScanCode() {
        Checkpoint finish = Checkpoint.builder().id(UUID.randomUUID()).checkpointType(Checkpoint.CheckpointType.FINISH)
            .selfScanToken("t").build();
        when(checkpointRepository.findByRaceIdAndId(raceId, finish.getId())).thenReturn(Optional.of(finish));

        assertThatThrownBy(() -> service.selfScanQrPng(raceId, finish.getId()))
            .isInstanceOf(ApiException.class).extracting("code").isEqualTo("SELF_SCAN_NOT_ALLOWED");
    }

    @Test
    void qrCodeEncodesTheSecretPayload() throws Exception {
        when(checkpointRepository.findByRaceIdAndId(raceId, checkpoint.getId())).thenReturn(Optional.of(checkpoint));

        byte[] png = service.selfScanQrPng(raceId, checkpoint.getId());

        java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(png));
        com.google.zxing.BinaryBitmap bitmap = new com.google.zxing.BinaryBitmap(new com.google.zxing.common.HybridBinarizer(
            new com.google.zxing.client.j2se.BufferedImageLuminanceSource(image)));
        String decoded = new com.google.zxing.qrcode.QRCodeReader().decode(bitmap).getText();
        assertThat(decoded).isEqualTo(checkpoint.getId() + ":" + raceId + ":t");
    }

    @Test
    void rotatingTheCodeReplacesTheSecretAndIsAudited() {
        when(checkpointRepository.findByRaceIdAndId(raceId, checkpoint.getId())).thenReturn(Optional.of(checkpoint));

        service.rotateSelfScanCode(raceId, checkpoint.getId());

        assertThat(checkpoint.getSelfScanToken()).hasSize(64).isNotEqualTo("t");
        verify(guard).assertCanManage(raceId);
        verify(audit).recordInCurrentTransaction(eq("CHECKPOINT_SELF_SCAN_CODE_ROTATED"), any(), eq("CHECKPOINT"),
            eq(checkpoint.getId().toString()), eq("SUCCESS"), any());
    }
}
