package com.racetracker.service.impl;

import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.model.Participant;
import com.racetracker.model.Race;
import com.racetracker.repository.CheckpointRepository;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.RaceRepository;
import com.racetracker.repository.RaceStageRepository;
import com.racetracker.service.RaceClosePolicy;
import com.racetracker.service.RaceCompletionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class RaceCompletionServiceImpl implements RaceCompletionService {

    private final RaceRepository raceRepository;
    private final ParticipantRepository participantRepository;
    private final CheckpointRepository checkpointRepository;
    private final RaceStageRepository raceStageRepository;
    private final Duration defaultTimeLimit;
    private final Duration completionGrace;

    public RaceCompletionServiceImpl(
        RaceRepository raceRepository,
        ParticipantRepository participantRepository,
        CheckpointRepository checkpointRepository,
        RaceStageRepository raceStageRepository,
        @Value("${racetracker.races.default-time-limit-hours:24}") long defaultTimeLimitHours,
        @Value("${racetracker.races.completion-grace-minutes:60}") long completionGraceMinutes
    ) {
        this.raceRepository = raceRepository;
        this.participantRepository = participantRepository;
        this.checkpointRepository = checkpointRepository;
        this.raceStageRepository = raceStageRepository;
        this.defaultTimeLimit = Duration.ofHours(Math.max(1, defaultTimeLimitHours));
        this.completionGrace = Duration.ofMinutes(Math.max(0, completionGraceMinutes));
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
        OffsetDateTime now = OffsetDateTime.now();
        LocalDate latestPossibleLocalDate = now.withOffsetSameInstant(ZoneOffset.UTC).toLocalDate().plusDays(1);
        List<Race> due = raceRepository
            .findByStatusAndRaceDateLessThanEqual(Race.RaceStatus.IN_PROGRESS, latestPossibleLocalDate)
            .stream()
            .filter(race -> isDue(race, now))
            .toList();
        due.forEach(this::completeRace);
        return due.size();
    }

    private boolean isDue(Race race, OffsetDateTime now) {
        OffsetDateTime closeAt = RaceClosePolicy.closeAt(
            race,
            raceStageRepository.findByRaceIdOrderBySequenceAsc(race.getId()),
            checkpointRepository.findByRaceIdOrderByOrderIndexAsc(race.getId()),
            defaultTimeLimit,
            completionGrace
        );
        return closeAt != null && !now.isBefore(closeAt);
    }
}
