package com.racetracker.service;

import com.racetracker.dto.checkpoint.CheckpointRecordReassignRequest;
import com.racetracker.dto.checkpoint.CheckpointRecordVoidRequest;
import com.racetracker.exception.ApiException;
import com.racetracker.exception.ConflictException;
import com.racetracker.mapper.CheckpointMapper;
import com.racetracker.model.Checkpoint;
import com.racetracker.model.CheckpointRecord;
import com.racetracker.model.CheckpointRecordCorrection;
import com.racetracker.model.Participant;
import com.racetracker.model.Race;
import com.racetracker.repository.CheckpointRecordCorrectionRepository;
import com.racetracker.repository.CheckpointRecordRepository;
import com.racetracker.repository.CheckpointRepository;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.RaceRepository;
import com.racetracker.repository.RfidReadEventRepository;
import com.racetracker.repository.UserRepository;
import com.racetracker.security.RaceAccessGuard;
import com.racetracker.service.impl.CheckpointServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TimingRecordVoidTest {

    private final CheckpointRepository checkpointRepository = mock(CheckpointRepository.class);
    private final CheckpointRecordRepository recordRepository = mock(CheckpointRecordRepository.class);
    private final ParticipantRepository participantRepository = mock(ParticipantRepository.class);
    private final RaceRepository raceRepository = mock(RaceRepository.class);
    private final CheckpointRecordCorrectionRepository corrections = mock(CheckpointRecordCorrectionRepository.class);
    private final RfidReadEventRepository readEvents = mock(RfidReadEventRepository.class);
    private final AuditEventService audit = mock(AuditEventService.class);
    private final RaceAccessGuard guard = mock(RaceAccessGuard.class);
    private final UserRepository users = mock(UserRepository.class);
    private CheckpointServiceImpl service;
    private Race race;
    private Checkpoint finish;
    private Participant wrongAthlete;
    private Participant rightAthlete;
    private CheckpointRecord record;
    private final List<CheckpointRecord> flushed = new java.util.ArrayList<>();

    @BeforeEach
    void setUp() {
        service = new CheckpointServiceImpl(checkpointRepository, recordRepository, participantRepository, raceRepository,
            users, mock(CheckpointMapper.class), mock(LiveSummaryCacheInvalidator.class),
            guard, mock(CheckpointProgression.class),
            new CutoffReevaluation(recordRepository, participantRepository), new ResultLock(raceRepository), audit,
            corrections, readEvents);
        when(guard.canManage(any())).thenReturn(true);
        race = Race.builder().id(UUID.randomUUID()).status(Race.RaceStatus.COMPLETED).build();
        finish = Checkpoint.builder().id(UUID.randomUUID()).race(race).name("Finish")
            .checkpointType(Checkpoint.CheckpointType.FINISH).selfScanToken("t").build();
        wrongAthlete = Participant.builder().id(UUID.randomUUID()).race(race).bibNumber("101")
            .status(Participant.ParticipantStatus.FINISHED).build();
        rightAthlete = Participant.builder().id(UUID.randomUUID()).race(race).bibNumber("202")
            .status(Participant.ParticipantStatus.STARTED).build();
        record = record(wrongAthlete, finish);
        when(raceRepository.findById(race.getId())).thenReturn(Optional.of(race));
        when(checkpointRepository.findByRaceIdAndId(race.getId(), finish.getId())).thenReturn(Optional.of(finish));
        when(recordRepository.findByRaceIdAndCheckpointIdAndId(race.getId(), finish.getId(), record.getId()))
            .thenReturn(Optional.of(record));
        when(recordRepository.findVoided(race.getId(), finish.getId(), record.getId())).thenReturn(Optional.of(record));
        when(recordRepository.saveAndFlush(any(CheckpointRecord.class))).thenAnswer(invocation -> {
            CheckpointRecord saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(UUID.randomUUID());
            }
            flushed.add(saved);
            return saved;
        });
        when(recordRepository.findByParticipantIdAndRaceIdOrderByRecordedAtAsc(any(), any())).thenReturn(List.of());
        when(participantRepository.findByRaceIdAndId(race.getId(), wrongAthlete.getId())).thenReturn(Optional.of(wrongAthlete));
        when(participantRepository.findByRaceIdAndId(race.getId(), rightAthlete.getId())).thenReturn(Optional.of(rightAthlete));
        when(participantRepository.findByRaceIdAndBibNumber(race.getId(), "202")).thenReturn(Optional.of(rightAthlete));
    }

    private CheckpointRecord record(Participant participant, Checkpoint checkpoint) {
        CheckpointRecord created = new CheckpointRecord();
        created.setId(UUID.randomUUID());
        created.setRace(race);
        created.setCheckpoint(checkpoint);
        created.setParticipant(participant);
        created.setRecordedAt(OffsetDateTime.parse("2026-11-15T09:30:00Z"));
        created.setRecordMethod(CheckpointRecord.RecordMethod.RFID);
        created.setIsVerified(true);
        created.setIsCutoffMissed(false);
        return created;
    }

    private CheckpointRecordVoidRequest reason(String text) {
        CheckpointRecordVoidRequest request = new CheckpointRecordVoidRequest();
        request.setReason(text);
        return request;
    }

    @Test
    void voidingTheFinishKeepsEvidenceAndReturnsTheAthleteToStarted() {
        service.voidRecord(race.getId(), finish.getId(), record.getId(), reason("Chip swapped with bib 202"));

        assertThat(record.getVoidedAt()).isNotNull();
        assertThat(record.getVoidReason()).isEqualTo("Chip swapped with bib 202");
        assertThat(wrongAthlete.getStatus()).isEqualTo(Participant.ParticipantStatus.STARTED);
        verify(recordRepository, never()).delete(any());
        verify(readEvents).voidForCheckpointRecord(eq(record.getId()), anyString());
        ArgumentCaptor<CheckpointRecordCorrection> history = ArgumentCaptor.forClass(CheckpointRecordCorrection.class);
        verify(corrections).save(history.capture());
        assertThat(history.getValue().getAction()).isEqualTo("VOIDED");
        verify(audit).recordInCurrentTransaction(eq("CHECKPOINT_RECORD_VOIDED"), any(), eq("CHECKPOINT_RECORD"),
            eq(record.getId().toString()), eq("SUCCESS"), any());
    }

    @Test
    void voidingTheReadThatMissedACutoffReversesTheCutoffDnf() {
        Checkpoint aid = Checkpoint.builder().id(UUID.randomUUID()).race(race).name("Aid 1")
            .checkpointType(Checkpoint.CheckpointType.INTERMEDIATE).cutoffTime(Duration.ofHours(1)).selfScanToken("a").build();
        CheckpointRecord late = record(wrongAthlete, aid);
        late.setIsCutoffMissed(true);
        wrongAthlete.setStatus(Participant.ParticipantStatus.DNF);
        when(checkpointRepository.findByRaceIdAndId(race.getId(), aid.getId())).thenReturn(Optional.of(aid));
        when(recordRepository.findByRaceIdAndCheckpointIdAndId(race.getId(), aid.getId(), late.getId()))
            .thenReturn(Optional.of(late));
        when(recordRepository.findVoided(race.getId(), aid.getId(), late.getId())).thenReturn(Optional.of(late));

        service.voidRecord(race.getId(), aid.getId(), late.getId(), reason("Read belonged to a sweeper"));

        assertThat(wrongAthlete.getStatus()).isEqualTo(Participant.ParticipantStatus.STARTED);
    }

    @Test
    void everyVoidNeedsAReason() {
        assertThatThrownBy(() -> service.voidRecord(race.getId(), finish.getId(), record.getId(), reason("  ")))
            .isInstanceOf(ApiException.class).extracting("code").isEqualTo("CORRECTION_REASON_REQUIRED");
        assertThat(record.getVoidedAt()).isNull();
    }

    @Test
    void certifiedResultsBlockVoiding() {
        race.setResultsCertifiedAt(OffsetDateTime.now());

        assertThatThrownBy(() -> service.voidRecord(race.getId(), finish.getId(), record.getId(), reason("Late protest upheld")))
            .isInstanceOf(ConflictException.class).extracting("code").isEqualTo(ResultLock.RESULTS_CERTIFIED);
    }

    @Test
    void restoreIsRefusedWhileTheAthleteAlreadyHasATimeThere() {
        record.setVoidedAt(OffsetDateTime.now());
        when(recordRepository.countByParticipantIdAndCheckpointId(wrongAthlete.getId(), finish.getId())).thenReturn(1L);

        assertThatThrownBy(() -> service.restoreRecord(race.getId(), finish.getId(), record.getId(), reason("Voided in error")))
            .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("TIMING_SLOT_OCCUPIED");
        verify(recordRepository, never()).restore(any(), any(Integer.class));
    }

    @Test
    void restoreBringsTheRecordBackAndReopensItsReads() {
        record.setVoidedAt(OffsetDateTime.now());
        wrongAthlete.setStatus(Participant.ParticipantStatus.STARTED);
        when(recordRepository.countByParticipantIdAndCheckpointId(wrongAthlete.getId(), finish.getId())).thenReturn(0L);

        service.restoreRecord(race.getId(), finish.getId(), record.getId(), reason("Voided in error"));

        verify(recordRepository).restore(record.getId(), 1);
        verify(readEvents).restoreForCheckpointRecord(record.getId());
        assertThat(wrongAthlete.getStatus()).isEqualTo(Participant.ParticipantStatus.FINISHED);
    }

    @Test
    void reassignMovesTheTimeToTheRightAthlete() {
        when(recordRepository.findByRaceIdAndCheckpointIdAndId(eq(race.getId()), eq(finish.getId()),
            org.mockito.ArgumentMatchers.argThat(id -> !record.getId().equals(id))))
            .thenAnswer(invocation -> flushed.stream()
                .filter(candidate -> candidate.getParticipant() == rightAthlete).findFirst());
        CheckpointRecordReassignRequest request = new CheckpointRecordReassignRequest();
        request.setParticipantBibNumber("202");
        request.setReason("Athletes swapped chips at pickup");

        service.reassignRecord(race.getId(), finish.getId(), record.getId(), request);

        CheckpointRecord moved = flushed.stream()
            .filter(candidate -> candidate.getParticipant() == rightAthlete).findFirst().orElseThrow();
        assertThat(moved.getRecordedAt()).isEqualTo(record.getRecordedAt());
        assertThat(moved.getNotes()).contains("101");
        assertThat(record.getVoidedAt()).isNotNull();
        assertThat(rightAthlete.getStatus()).isEqualTo(Participant.ParticipantStatus.FINISHED);
        assertThat(wrongAthlete.getStatus()).isEqualTo(Participant.ParticipantStatus.STARTED);
        verify(readEvents).reassignCheckpointRecord(eq(record.getId()), eq(moved.getId()), eq(rightAthlete.getId()), anyString());
        verify(readEvents, never()).voidForCheckpointRecord(any(), anyString());
    }

    @Test
    void reassigningToTheSameAthleteIsRejected() {
        CheckpointRecordReassignRequest request = new CheckpointRecordReassignRequest();
        request.setParticipantId(wrongAthlete.getId());
        request.setReason("Same athlete by mistake");

        assertThatThrownBy(() -> service.reassignRecord(race.getId(), finish.getId(), record.getId(), request))
            .isInstanceOf(ApiException.class).extracting("code").isEqualTo("REASSIGN_SAME_PARTICIPANT");
        assertThat(record.getVoidedAt()).isNull();
    }

    private com.racetracker.model.User signInAs(String email) {
        com.racetracker.model.User staff = com.racetracker.model.User.builder().id(UUID.randomUUID()).email(email).build();
        when(users.findByEmailIgnoreCase(email)).thenReturn(Optional.of(staff));
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(email, "n/a", java.util.List.of()));
        when(guard.canManage(any())).thenReturn(false);
        return staff;
    }

    @org.junit.jupiter.api.AfterEach
    void clearSecurity() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    void staffCanUndoTheirOwnManualEntryWithinFiveMinutes() {
        com.racetracker.model.User volunteer = signInAs("volunteer@example.com");
        record.setRecordMethod(CheckpointRecord.RecordMethod.MANUAL);
        record.setRecordedBy(volunteer);
        record.setCreatedAt(OffsetDateTime.now().minusMinutes(2));

        service.voidRecord(race.getId(), finish.getId(), record.getId(), reason("Typed the wrong bib"));

        verify(guard).assertCanAccessCheckpoint(race.getId(), finish.getId());
        assertThat(record.getVoidedAt()).isNotNull();
    }

    @Test
    void staffCannotUndoSomeoneElsesEntryOrAnOldOne() {
        signInAs("volunteer@example.com");
        record.setRecordMethod(CheckpointRecord.RecordMethod.MANUAL);
        record.setRecordedBy(com.racetracker.model.User.builder().id(UUID.randomUUID()).email("other@example.com").build());
        record.setCreatedAt(OffsetDateTime.now().minusMinutes(1));
        assertThatThrownBy(() -> service.voidRecord(race.getId(), finish.getId(), record.getId(), reason("Typed the wrong bib")))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);

        com.racetracker.model.User volunteer = signInAs("volunteer@example.com");
        record.setRecordedBy(volunteer);
        record.setCreatedAt(OffsetDateTime.now().minusMinutes(6));
        assertThatThrownBy(() -> service.voidRecord(race.getId(), finish.getId(), record.getId(), reason("Typed the wrong bib")))
            .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("RECORD_UNDO_WINDOW_EXPIRED");

        record.setCreatedAt(OffsetDateTime.now());
        record.setRecordMethod(CheckpointRecord.RecordMethod.RFID);
        assertThatThrownBy(() -> service.voidRecord(race.getId(), finish.getId(), record.getId(), reason("Typed the wrong bib")))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThat(record.getVoidedAt()).isNull();
    }
}
