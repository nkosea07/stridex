package com.racetracker.service;

import java.util.UUID;

public interface RfidReadEventProcessor {
    void process(UUID eventId);

    /**
     * Records a failed processing attempt in its own transaction. Returns true when the event
     * exhausted its attempts and was marked ERROR.
     */
    boolean recordProcessingFailure(UUID eventId, Exception failure);
}
