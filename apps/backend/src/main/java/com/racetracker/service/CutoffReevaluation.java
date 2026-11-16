package com.racetracker.service;

import com.racetracker.model.Checkpoint;
import com.racetracker.model.CheckpointRecord;
import com.racetracker.model.Participant;
import com.racetracker.repository.CheckpointRecordRepository;
import com.racetracker.repository.ParticipantRepository;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Re-applies every cutoff for a participant after one of their timing records changed
 * (official correction, an earlier RFID read replacing a later one, or a voided record) and
 * reverses a DNF that existed only because of a cutoff that no longer applies. Removing the finish
 * record returns a finisher to STARTED.
 */
@Component
public class CutoffReevaluation {

    private final CheckpointRecordRepository checkpointRecordRepository;
    private final ParticipantRepository participantRepository;

    public CutoffReevaluation(CheckpointRecordRepository checkpointRecordRepository,
                              ParticipantRepository participantRepository) {
        this.checkpointRecordRepository = checkpointRecordRepository;
        this.participantRepository = participantRepository;
    }

    public void reevaluate(Participant participant, UUID raceId) {
        reevaluate(participant, raceId, false, false);
    }

    public void reevaluateAfterRemoval(Participant participant, UUID raceId, boolean removedMissedCutoff,
                                       boolean removedWasFinish) {
        reevaluate(participant, raceId, removedMissedCutoff, removedWasFinish);
    }

    private void reevaluate(Participant participant, UUID raceId, boolean removedMissedCutoff, boolean removedWasFinish) {
        List<CheckpointRecord> records = checkpointRecordRepository
            .findByParticipantIdAndRaceIdOrderByRecordedAtAsc(participant.getId(), raceId);
        OffsetDateTime start = CheckpointCutoff.resolveStart(participant, records);
        boolean previouslyMissed = removedMissedCutoff;
        boolean nowMissed = false;
        boolean hasFinishRecord = false;
        for (CheckpointRecord existing : records) {
            Checkpoint checkpoint = existing.getCheckpoint();
            boolean wasMissed = Boolean.TRUE.equals(existing.getIsCutoffMissed());
            boolean missed = checkpoint != null
                && CheckpointCutoff.isMissed(checkpoint.getCutoffTime(), start, CheckpointCutoff.effectiveTime(existing));
            previouslyMissed |= wasMissed;
            nowMissed |= missed;
            hasFinishRecord |= CheckpointPasses.isFinish(existing);
            if (missed != wasMissed) {
                existing.setIsCutoffMissed(missed);
                checkpointRecordRepository.save(existing);
            }
        }
        Participant.ParticipantStatus current = participant.getStatus();
        if (removedWasFinish && current == Participant.ParticipantStatus.FINISHED && !hasFinishRecord) {
            current = Participant.ParticipantStatus.STARTED;
        }
        Participant.ParticipantStatus next = CheckpointProgression.statusAfterCutoffReevaluation(
            current, previouslyMissed, nowMissed, hasFinishRecord);
        if (next != participant.getStatus()) {
            participant.setStatus(next);
            participantRepository.save(participant);
        }
    }
}
