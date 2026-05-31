package com.racetracker.websocket;

import com.racetracker.dto.tracking.LiveTrackingResponse;

import java.util.UUID;

public interface TrackingLivePublisher {
    void publishLocationUpdate(UUID raceId, LiveTrackingResponse payload);
}
