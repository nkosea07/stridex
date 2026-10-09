package com.racetracker.service;

import com.racetracker.model.RfidReadEvent;

import java.util.UUID;

public interface RfidEventPublisher {
    void publish(UUID eventId);
    void publish(RfidReadEvent event);
}
