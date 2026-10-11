package com.racetracker.service;

import com.racetracker.dto.checkpoint.*;

import java.util.List;
import java.util.UUID;

public interface CheckpointService {
    List<CheckpointDto> listByRace(UUID raceId);
    CheckpointDto create(UUID raceId, CheckpointCreateRequest request);
    CheckpointDto update(UUID raceId, UUID checkpointId, CheckpointUpdateRequest request);
    void delete(UUID raceId, UUID checkpointId);
    CheckpointRecordDto record(UUID raceId, UUID checkpointId, CheckpointRecordRequest request);
    CheckpointRecordDto correctRecord(UUID raceId, UUID checkpointId, UUID recordId,
                                       com.racetracker.dto.checkpoint.CheckpointRecordCorrectionRequest request);
}
