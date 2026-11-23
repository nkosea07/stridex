package com.racetracker.service;

import com.racetracker.model.Checkpoint;
import com.racetracker.model.Participant;
import com.racetracker.model.Participant.ParticipantStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CheckpointProgressionTest {

    private final SpectatorAlertService alerts = mock(SpectatorAlertService.class);
    private final CheckpointProgression progression = new CheckpointProgression(alerts);

    @Test
    void intermediateCrossingStartsCheckedInParticipantAndAlertsOnce() {
        Participant participant = Participant.builder().status(ParticipantStatus.CHECKED_IN).build();

        progression.apply(participant, checkpoint(Checkpoint.CheckpointType.INTERMEDIATE), false);

        assertThat(participant.getStatus()).isEqualTo(ParticipantStatus.STARTED);
        verify(alerts).onStarted(participant);
    }

    @Test
    void laterIntermediateCrossingDoesNotRepeatStartedAlert() {
        Participant participant = Participant.builder().status(ParticipantStatus.STARTED).build();
        Checkpoint intermediate = checkpoint(Checkpoint.CheckpointType.INTERMEDIATE);

        progression.apply(participant, intermediate, false);

        assertThat(participant.getStatus()).isEqualTo(ParticipantStatus.STARTED);
        verify(alerts).onCheckpoint(participant, intermediate);
        verify(alerts, never()).onStarted(participant);
    }

    @Test
    void finishCrossingMarksFinished() {
        Participant participant = Participant.builder().status(ParticipantStatus.STARTED).build();

        progression.apply(participant, checkpoint(Checkpoint.CheckpointType.FINISH), false);

        assertThat(participant.getStatus()).isEqualTo(ParticipantStatus.FINISHED);
        verify(alerts).onFinished(participant);
    }

    @Test
    void finishCrossingNeverOverridesDisqualification() {
        Participant participant = Participant.builder().status(ParticipantStatus.DISQUALIFIED).build();

        progression.apply(participant, checkpoint(Checkpoint.CheckpointType.FINISH), false);

        assertThat(participant.getStatus()).isEqualTo(ParticipantStatus.DISQUALIFIED);
        verify(alerts, never()).onFinished(participant);
    }

    @Test
    void missedCutoffMarksActiveParticipantDnfButNotFinisher() {
        assertThat(CheckpointProgression.nextStatus(ParticipantStatus.STARTED,
            checkpoint(Checkpoint.CheckpointType.INTERMEDIATE), true)).isEqualTo(ParticipantStatus.DNF);
        assertThat(CheckpointProgression.nextStatus(ParticipantStatus.FINISHED,
            checkpoint(Checkpoint.CheckpointType.INTERMEDIATE), true)).isEqualTo(ParticipantStatus.FINISHED);
    }

    @Test
    void nonEntrantStatusesAreNotAdvancedByTimingReads() {
        assertThat(CheckpointProgression.nextStatus(ParticipantStatus.WAITLISTED,
            checkpoint(Checkpoint.CheckpointType.FINISH), false)).isEqualTo(ParticipantStatus.WAITLISTED);
        assertThat(CheckpointProgression.nextStatus(ParticipantStatus.DNS,
            checkpoint(Checkpoint.CheckpointType.FINISH), false)).isEqualTo(ParticipantStatus.DNS);
    }

    private Checkpoint checkpoint(Checkpoint.CheckpointType type) {
        return Checkpoint.builder().checkpointType(type).build();
    }

    @Test
    void onlyAcceptedEntrantsCanBeTimed() {
        assertThat(CheckpointProgression.isTimeableEntrant(ParticipantStatus.CONFIRMED)).isTrue();
        assertThat(CheckpointProgression.isTimeableEntrant(ParticipantStatus.DISQUALIFIED)).isTrue();
        assertThat(CheckpointProgression.isTimeableEntrant(ParticipantStatus.WAITLISTED)).isFalse();
        assertThat(CheckpointProgression.isTimeableEntrant(ParticipantStatus.PENDING_REVIEW)).isFalse();
        assertThat(CheckpointProgression.isTimeableEntrant(ParticipantStatus.REJECTED)).isFalse();
        assertThat(CheckpointProgression.isTimeableEntrant(ParticipantStatus.WITHDRAWN)).isFalse();
    }

    @Test
    void correctionStatusRulesRespectOfficialDecisions() {
        assertThat(CheckpointProgression.statusAfterCutoffReevaluation(ParticipantStatus.FINISHED, false, true, true))
            .isEqualTo(ParticipantStatus.DNF);
        assertThat(CheckpointProgression.statusAfterCutoffReevaluation(ParticipantStatus.DNF, true, false, true))
            .isEqualTo(ParticipantStatus.FINISHED);
        assertThat(CheckpointProgression.statusAfterCutoffReevaluation(ParticipantStatus.DNF, true, false, false))
            .isEqualTo(ParticipantStatus.STARTED);
        assertThat(CheckpointProgression.statusAfterCutoffReevaluation(ParticipantStatus.DNF, false, false, true))
            .isEqualTo(ParticipantStatus.DNF);
        assertThat(CheckpointProgression.statusAfterCutoffReevaluation(ParticipantStatus.DISQUALIFIED, false, true, true))
            .isEqualTo(ParticipantStatus.DISQUALIFIED);
    }
}
