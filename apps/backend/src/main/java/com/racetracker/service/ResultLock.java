package com.racetracker.service;

import com.racetracker.exception.ConflictException;
import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.model.Race;
import com.racetracker.repository.RaceRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Once results are certified official, nothing that feeds them may change: timing records,
 * corrections, penalties, result-affecting participant details, checkpoints, gun time, and
 * reprocessing. A race admin must reopen the results (with a recorded reason) first.
 */
@Component
public class ResultLock {

    public static final String RESULTS_CERTIFIED = "RESULTS_CERTIFIED";

    private final RaceRepository raceRepository;

    public ResultLock(RaceRepository raceRepository) {
        this.raceRepository = raceRepository;
    }

    public void assertOpen(UUID raceId) {
        assertOpen(raceRepository.findById(raceId).orElseThrow(() -> new ResourceNotFoundException("Race not found")));
    }

    public void assertOpen(Race race) {
        if (isCertified(race)) {
            throw new ConflictException(
                "Results for this race are certified official. Reopen the results before changing timing data.",
                RESULTS_CERTIFIED);
        }
    }

    public static boolean isCertified(Race race) {
        return race != null && race.getResultsCertifiedAt() != null;
    }
}
