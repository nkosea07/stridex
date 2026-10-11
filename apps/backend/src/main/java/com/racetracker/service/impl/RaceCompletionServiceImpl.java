package com.racetracker.service.impl;

import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.model.Participant;
import com.racetracker.model.Race;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.RaceRepository;
import com.racetracker.service.RaceCompletionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class RaceCompletionServiceImpl implements RaceCompletionService {

    private final RaceRepository raceRepository;
    private final ParticipantRepository participantRepository;

    public RaceCompletionServiceImpl(
        RaceRepository raceRepository,
        ParticipantRepository participantRepository
    ) {
        this.raceRepository = raceRepository;
        this.participantRepository = participantRepository;
    }

    @Override
    @Transactional
    public Race completeRace(UUID raceId) {
        Race race = raceRepository.findById(raceId)
            .orElseThrow(() -> new ResourceNotFoundException("Race not found"));
        return completeRace(race);
    }

    private Race completeRace(Race race) {
        boolean changed = false;
        if (race.getStatus() != Race.RaceStatus.COMPLETED) {
            race.setStatus(Race.RaceStatus.COMPLETED);
            changed = true;
        }
        if (race.getCompletedAt() == null) {
            race.setCompletedAt(OffsetDateTime.now());
            changed = true;
        }
        if (changed) {
            raceRepository.save(race);
        }

        List<Participant> starters = participantRepository
            .findByRaceIdAndStatusOrderByRegisteredAtAsc(race.getId(), Participant.ParticipantStatus.STARTED);
        if (!starters.isEmpty()) {
            starters.forEach(participant -> participant.setStatus(Participant.ParticipantStatus.DNF));
            participantRepository.saveAll(starters);
        }
        return race;
    }

    @Override
    @Transactional
    public int autoCompleteDueRaces() {
        List<Race> due = raceRepository.findByStatusAndRaceDateBefore(
            Race.RaceStatus.IN_PROGRESS, LocalDate.now());
        due.forEach(this::completeRace);
        return due.size();
    }
}
