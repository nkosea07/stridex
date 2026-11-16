package com.racetracker.service;

import com.racetracker.exception.ApiException;
import com.racetracker.exception.ConflictException;
import com.racetracker.model.Participant;
import com.racetracker.model.Race;
import com.racetracker.repository.CheckpointRecordRepository;
import com.racetracker.repository.ParticipantPenaltyRepository;
import com.racetracker.repository.RaceRepository;
import com.racetracker.repository.RaceResultRepository;
import com.racetracker.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ParticipantChangeControlTest {

    private final AuditEventService audit = mock(AuditEventService.class);
    private final CheckpointRecordRepository records = mock(CheckpointRecordRepository.class);
    private ParticipantChangeControl control;
    private Race race;
    private Participant participant;

    @BeforeEach
    void setUp() {
        control = new ParticipantChangeControl(new ResultLock(mock(RaceRepository.class)), audit,
            mock(UserRepository.class), records, mock(ParticipantPenaltyRepository.class), mock(RaceResultRepository.class));
        race = Race.builder().id(UUID.randomUUID()).status(Race.RaceStatus.COMPLETED).build();
        participant = Participant.builder().id(UUID.randomUUID()).race(race).bibNumber("101")
            .status(Participant.ParticipantStatus.FINISHED).build();
    }

    @Test
    void resultAffectingChangesAreAuditedWithBeforeAndAfter() {
        ParticipantChangeControl.Snapshot before = control.snapshot(participant);
        participant.setBibNumber("110");

        control.verifyAndRecord(before, participant, null);

        verify(audit).recordInCurrentTransaction(eq("PARTICIPANT_RESULT_DETAILS_CHANGED"), any(), eq("PARTICIPANT"),
            eq(participant.getId().toString()), eq("SUCCESS"),
            eq(Map.of("bibNumber", Map.of("from", "101", "to", "110"))));
    }

    @Test
    void disqualifyingRequiresAReasonAndStoresIt() {
        ParticipantChangeControl.Snapshot before = control.snapshot(participant);
        participant.setStatus(Participant.ParticipantStatus.DISQUALIFIED);

        assertThatThrownBy(() -> control.verifyAndRecord(before, participant, " "))
            .isInstanceOf(ApiException.class).extracting("code").isEqualTo("STATUS_REASON_REQUIRED");

        control.verifyAndRecord(before, participant, "Course cut between 10 km and 15 km");
        assertThat(participant.getStatusReason()).isEqualTo("Course cut between 10 km and 15 km");
    }

    @Test
    void certifiedResultsBlockResultAffectingChanges() {
        race.setResultsCertifiedAt(OffsetDateTime.now());
        ParticipantChangeControl.Snapshot before = control.snapshot(participant);
        participant.setBibNumber("999");

        assertThatThrownBy(() -> control.verifyAndRecord(before, participant, null))
            .isInstanceOf(ConflictException.class).extracting("code").isEqualTo(ResultLock.RESULTS_CERTIFIED);
        verify(audit, never()).recordInCurrentTransaction(any(), any(), any(), any(), any(), anyMap());
    }

    @Test
    void entriesWithTimingOrAStartedRaceCannotBeDeleted() {
        when(records.existsIncludingVoidedByParticipantId(participant.getId())).thenReturn(true);
        race.setStatus(Race.RaceStatus.REGISTRATION_OPEN);

        assertThatThrownBy(() -> control.verifyDeletable(participant))
            .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("PARTICIPANT_HAS_TIMING_DATA");

        when(records.existsIncludingVoidedByParticipantId(participant.getId())).thenReturn(false);
        race.setStatus(Race.RaceStatus.IN_PROGRESS);
        assertThatThrownBy(() -> control.verifyDeletable(participant)).isInstanceOf(ConflictException.class);

        race.setStatus(Race.RaceStatus.REGISTRATION_OPEN);
        control.verifyDeletable(participant);
    }
}
