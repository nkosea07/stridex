package com.racetracker.service;

import com.racetracker.model.Checkpoint;
import com.racetracker.model.Participant;
import com.racetracker.model.Participant.ParticipantStatus;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Set;

/**
 * Applies the participant status transition for an accepted checkpoint record and notifies
 * followers. Every timing path (RFID, staff manual/QR, timing import, athlete self-scan) uses
 * this so a crossing has the same effect regardless of how it was captured. Terminal or
 * official decisions (FINISHED, DNF, DNS, DISQUALIFIED) and non-entrant states are never
 * overwritten by a timing read.
 */
@Component
public class CheckpointProgression {

    private static final Set<ParticipantStatus> TIMEABLE = EnumSet.of(
        ParticipantStatus.REGISTERED,
        ParticipantStatus.CONFIRMED,
        ParticipantStatus.CHECKED_IN,
        ParticipantStatus.STARTED
    );

    private static final Set<ParticipantStatus> NON_ENTRANTS = EnumSet.of(
        ParticipantStatus.PENDING_REVIEW,
        ParticipantStatus.WAITLISTED,
        ParticipantStatus.REJECTED
    );

    private static final Set<ParticipantStatus> CUTOFF_DNF_ELIGIBLE = EnumSet.of(
        ParticipantStatus.REGISTERED,
        ParticipantStatus.CONFIRMED,
        ParticipantStatus.CHECKED_IN,
        ParticipantStatus.STARTED,
        ParticipantStatus.FINISHED
    );

    private final SpectatorAlertService spectatorAlertService;

    public CheckpointProgression(SpectatorAlertService spectatorAlertService) {
        this.spectatorAlertService = spectatorAlertService;
    }

    public void apply(Participant participant, Checkpoint checkpoint, boolean cutoffMissed) {
        ParticipantStatus previous = participant.getStatus();
        ParticipantStatus next = nextStatus(previous, checkpoint, cutoffMissed);
        if (next != previous) {
            participant.setStatus(next);
        }

        spectatorAlertService.onCheckpoint(participant, checkpoint);
        if (next == previous) {
            return;
        }
        if (next == ParticipantStatus.FINISHED) {
            spectatorAlertService.onFinished(participant);
        } else if (next == ParticipantStatus.STARTED) {
            spectatorAlertService.onStarted(participant);
        }
    }

    public static ParticipantStatus nextStatus(ParticipantStatus previous, Checkpoint checkpoint, boolean cutoffMissed) {
        ParticipantStatus current = previous == null ? ParticipantStatus.REGISTERED : previous;
        if (!TIMEABLE.contains(current)) {
            return previous;
        }
        if (cutoffMissed) {
            return ParticipantStatus.DNF;
        }
        if (checkpoint != null && checkpoint.getCheckpointType() == Checkpoint.CheckpointType.FINISH) {
            return ParticipantStatus.FINISHED;
        }
        return ParticipantStatus.STARTED;
    }

    /**
     * Pending-approval, waitlisted, and rejected registrations are not in the race, so timing
     * paths must not create checkpoint records for them.
     */
    public static boolean isTimeableEntrant(ParticipantStatus status) {
        return status == null || !NON_ENTRANTS.contains(status);
    }

    /**
     * Status after an official time correction re-evaluated every cutoff for the participant.
     * A newly missed cutoff makes an active or finished participant DNF; a DNF that existed only
     * because of a cutoff that no longer applies is reversed. Other official decisions stand.
     */
    public static ParticipantStatus statusAfterCutoffReevaluation(ParticipantStatus current, boolean previouslyMissed,
                                                                  boolean nowMissed, boolean hasFinishRecord) {
        if (nowMissed) {
            return current != null && CUTOFF_DNF_ELIGIBLE.contains(current) ? ParticipantStatus.DNF : current;
        }
        if (previouslyMissed && current == ParticipantStatus.DNF) {
            return hasFinishRecord ? ParticipantStatus.FINISHED : ParticipantStatus.STARTED;
        }
        return current;
    }
}
