package com.racetracker.service;

import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.model.Race;
import com.racetracker.repository.RaceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PublicRaceVisibility {

    private final RaceRepository raceRepository;

    public PublicRaceVisibility(RaceRepository raceRepository) {
        this.raceRepository = raceRepository;
    }

    @Transactional(readOnly = true)
    public Race requireVisible(UUID raceId) {
        Race race = raceRepository.findByIdForPublicVisibility(raceId)
            .orElseThrow(() -> new ResourceNotFoundException("Race not found"));
        if (!isVisible(race)) {
            throw new ResourceNotFoundException("Race not found");
        }
        return race;
    }

    public static boolean isVisible(Race race) {
        return race != null
            && Boolean.TRUE.equals(race.getIsPublicRegistration())
            && race.getStatus() != null
            && race.getStatus() != Race.RaceStatus.DRAFT
            && race.getArchivedAt() == null;
    }
}
