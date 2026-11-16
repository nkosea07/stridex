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
    CheckpointRecordDto recordImported(UUID raceId, UUID checkpointId, CheckpointRecordRequest request);

    List<CheckpointRecordDto> listRecords(UUID raceId, UUID checkpointId);
    List<CheckpointRecordDto> listRecords(UUID raceId, UUID checkpointId, boolean includeVoided);
    List<CheckpointRecordDto> listRecentRecords(UUID raceId, UUID checkpointId, int limit);
    CheckpointRecordDto voidRecord(UUID raceId, UUID checkpointId, UUID recordId, CheckpointRecordVoidRequest request);
    CheckpointRecordDto restoreRecord(UUID raceId, UUID checkpointId, UUID recordId, CheckpointRecordVoidRequest request);
    CheckpointRecordDto reassignRecord(UUID raceId, UUID checkpointId, UUID recordId, CheckpointRecordReassignRequest request);
    CheckpointRecordDto correctRecord(UUID raceId, UUID checkpointId, UUID recordId,
                                       com.racetracker.dto.checkpoint.CheckpointRecordCorrectionRequest request);
}
