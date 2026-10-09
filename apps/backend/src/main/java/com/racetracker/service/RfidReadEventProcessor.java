package com.racetracker.service;

import java.util.UUID;

public interface RfidReadEventProcessor {
    void process(UUID eventId);
}
