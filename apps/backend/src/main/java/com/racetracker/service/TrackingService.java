package com.racetracker.service;

import com.racetracker.dto.tracking.*;

import java.util.List;
import java.util.UUID;

public interface TrackingService {
    void recordLocation(UUID raceId, GpsLocationRequest request);
    void recordLocationsBatch(UUID raceId, GpsLocationBatchRequest request);
    SelfScanResultDto recordCheckpoint(UUID raceId, TrackingCheckpointRequest request);

    void publishTimingUpdate(UUID raceId, UUID participantId);

    List<LiveTrackingResponse> getLiveData(UUID raceId);
    ParticipantTrackResponse getParticipantTrack(UUID raceId, UUID participantId);
}
