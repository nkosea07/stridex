package com.racetracker.service;

import com.racetracker.repository.CheckpointRecordRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class LastCheckpointNameCache {

    private final CheckpointRecordRepository checkpointRecordRepository;

    public LastCheckpointNameCache(CheckpointRecordRepository checkpointRecordRepository) {
        this.checkpointRecordRepository = checkpointRecordRepository;
    }

    @Cacheable(value = "lastCheckpoint", key = "#participantId + '-' + #raceId")
    public String getName(UUID participantId, UUID raceId) {
        return checkpointRecordRepository.findTopByParticipantIdAndRaceIdOrderByRecordedAtDesc(participantId, raceId)
            .map(record -> record.getCheckpoint() != null ? record.getCheckpoint().getName() : null)
            .orElse(null);
    }
}
