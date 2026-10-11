package com.racetracker.service;

import com.racetracker.model.Race;

import java.util.UUID;

public interface RaceCompletionService {

    /**
     * Marks a race COMPLETED, stamps the completion instant, and resolves unfinished
     * starters as DNF. Idempotent for already-completed races.
     */
    Race completeRace(UUID raceId);

    /**
     * Completes every IN_PROGRESS race whose race date has passed. Returns the number of
     * races completed.
     */
    int autoCompleteDueRaces();
}
