package com.racetracker.service;

import com.racetracker.model.CheckpointRecord;
import com.racetracker.model.Participant;
import com.racetracker.model.RfidReadEvent;
import com.racetracker.repository.CheckpointRecordRepository;
import com.racetracker.repository.RfidReadEventRepository;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Applies a new or corrected gun time to timing already recorded. Automatic RFID times earlier
 * than the gun move to the athlete's earliest read at or after the gun, or are removed when no such
 * read exists; reads that were ignored as pre-gun are re-run so post-gun reads count. Official
 * corrections and manual times are never touched.
 */
@Component
public class TimingRegate {

    public record Outcome(int moved, int removed, int requeued) {
    }

    private static final EnumSet<RfidReadEvent.RfidReadEventStatus> TIMING_READS = EnumSet.of(
        RfidReadEvent.RfidReadEventStatus.ACCEPTED,
        RfidReadEvent.RfidReadEventStatus.DUPLICATE
    );

    private final CheckpointRecordRepository checkpointRecordRepository;
    private final RfidReadEventRepository rfidReadEventRepository;
    private final CutoffReevaluation cutoffReevaluation;
    private final LiveSummaryCacheInvalidator liveSummaryCacheInvalidator;

    public TimingRegate(CheckpointRecordRepository checkpointRecordRepository,
                        RfidReadEventRepository rfidReadEventRepository,
                        CutoffReevaluation cutoffReevaluation,
                        LiveSummaryCacheInvalidator liveSummaryCacheInvalidator) {
        this.checkpointRecordRepository = checkpointRecordRepository;
        this.rfidReadEventRepository = rfidReadEventRepository;
        this.cutoffReevaluation = cutoffReevaluation;
        this.liveSummaryCacheInvalidator = liveSummaryCacheInvalidator;
    }

    public Outcome apply(UUID raceId, UUID waveId, OffsetDateTime gate) {
        List<CheckpointRecord> early = waveId == null
            ? checkpointRecordRepository.findAutomaticBeforeGateWithoutWave(raceId, gate)
            : checkpointRecordRepository.findAutomaticBeforeGateInWave(raceId, waveId, gate);
        int moved = 0;
        int removed = 0;
        Map<UUID, Participant> affected = new LinkedHashMap<>();
        for (CheckpointRecord record : early) {
            Participant participant = record.getParticipant();
            affected.put(participant.getId(), participant);
            RfidReadEvent firstValid = rfidReadEventRepository
                .findFirstByMatchedParticipantIdAndCheckpointIdAndReadAtGreaterThanEqualAndStatusInOrderByReadAtAsc(
                    participant.getId(), record.getCheckpoint().getId(), gate, TIMING_READS)
                .orElse(null);
            if (firstValid != null) {
                record.setRecordedAt(firstValid.getReadAt());
                checkpointRecordRepository.save(record);
                moved++;
            } else {
                checkpointRecordRepository.delete(record);
                removed++;
            }
        }
        checkpointRecordRepository.flush();
        for (Participant participant : affected.values()) {
            cutoffReevaluation.reevaluate(participant, raceId);
            liveSummaryCacheInvalidator.evictLastCheckpointAfterCommit(participant.getId(), raceId);
        }
        int requeued = rfidReadEventRepository.requeueByRaceIdAndStatusIn(raceId,
            EnumSet.of(RfidReadEvent.RfidReadEventStatus.IGNORED));
        return new Outcome(moved, removed, requeued);
    }
}
