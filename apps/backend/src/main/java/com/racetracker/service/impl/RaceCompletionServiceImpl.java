package com.racetracker.service.impl;

import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.model.Participant;
import com.racetracker.model.Race;
import com.racetracker.repository.CheckpointRepository;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.RaceRepository;
import com.racetracker.repository.RaceStageRepository;
import com.racetracker.service.AuditEventService;
import com.racetracker.service.RaceClosePolicy;
import com.racetracker.service.RaceCompletionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class RaceCompletionServiceImpl implements RaceCompletionService {

    private static final String NOT_FINISHED_AT_COMPLETION = "Did not finish before the race was completed";
    private static final String NOT_STARTED_AT_COMPLETION = "Did not start before the race was completed";
    private static final Set<Participant.ParticipantStatus> NOT_STARTED = EnumSet.of(
        Participant.ParticipantStatus.REGISTERED,
        Participant.ParticipantStatus.CONFIRMED,
        Participant.ParticipantStatus.CHECKED_IN
    );

    private final RaceRepository raceRepository;
    private final ParticipantRepository participantRepository;
    private final CheckpointRepository checkpointRepository;
    private final RaceStageRepository raceStageRepository;
    private final AuditEventService auditEventService;
    private final Duration defaultTimeLimit;
    private final Duration completionGrace;

    public RaceCompletionServiceImpl(
        RaceRepository raceRepository,
        ParticipantRepository participantRepository,
        CheckpointRepository checkpointRepository,
        RaceStageRepository raceStageRepository,
        AuditEventService auditEventService,
        @Value("${racetracker.races.default-time-limit-hours:24}") long defaultTimeLimitHours,
        @Value("${racetracker.races.completion-grace-minutes:60}") long completionGraceMinutes
    ) {
        this.raceRepository = raceRepository;
        this.participantRepository = participantRepository;
        this.checkpointRepository = checkpointRepository;
        this.raceStageRepository = raceStageRepository;
        this.auditEventService = auditEventService;
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
        starters.forEach(participant -> {
            participant.setStatus(Participant.ParticipantStatus.DNF);
            participant.setStatusReason(NOT_FINISHED_AT_COMPLETION);
        });
        List<Participant> nonStarters = participantRepository
            .findByRaceIdAndStatusInOrderByRegisteredAtAsc(race.getId(), NOT_STARTED);
        nonStarters.forEach(participant -> {
            participant.setStatus(Participant.ParticipantStatus.DNS);
            participant.setStatusReason(NOT_STARTED_AT_COMPLETION);
        });
        if (!starters.isEmpty() || !nonStarters.isEmpty()) {
            List<Participant> resolved = new java.util.ArrayList<>(starters);
            resolved.addAll(nonStarters);
            participantRepository.saveAll(resolved);
            auditEventService.recordForActor("RACE_COMPLETION_STATUSES_RESOLVED", currentActorEmail(), "RACE",
                race.getId().toString(), "SUCCESS", Map.of("dnf", starters.size(), "dns", nonStarters.size()));
        }
        return race;
    }

    private static String currentActorEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication == null || !authentication.isAuthenticated() ? null : authentication.getName();
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
