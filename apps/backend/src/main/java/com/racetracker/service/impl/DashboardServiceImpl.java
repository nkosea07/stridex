package com.racetracker.service.impl;

import com.racetracker.dto.dashboard.DashboardStatsDto;
import com.racetracker.model.Race;
import com.racetracker.repository.CheckpointRecordRepository;
import com.racetracker.repository.GpsLocationRepository;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.RaceRepository;
import com.racetracker.service.DashboardService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class DashboardServiceImpl implements DashboardService {

    private final RaceRepository raceRepository;
    private final ParticipantRepository participantRepository;
    private final GpsLocationRepository gpsLocationRepository;
    private final CheckpointRecordRepository checkpointRecordRepository;

    public DashboardServiceImpl(
        RaceRepository raceRepository,
        ParticipantRepository participantRepository,
        GpsLocationRepository gpsLocationRepository,
        CheckpointRecordRepository checkpointRecordRepository
    ) {
        this.raceRepository = raceRepository;
        this.participantRepository = participantRepository;
        this.gpsLocationRepository = gpsLocationRepository;
        this.checkpointRecordRepository = checkpointRecordRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardStatsDto getStats() {
        OffsetDateTime liveSince = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(10);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        OffsetDateTime dayStart = today.atStartOfDay().atOffset(ZoneOffset.UTC);
        List<Race.RaceStatus> activeStatuses = List.of(Race.RaceStatus.IN_PROGRESS, Race.RaceStatus.REGISTRATION_OPEN);

        org.springframework.security.core.Authentication authentication =
            org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        boolean superAdmin = authentication != null && authentication.getAuthorities().stream()
            .anyMatch(authority -> "ROLE_SUPER_ADMIN".equals(authority.getAuthority()));
        if (superAdmin) {
            return new DashboardStatsDto(
                raceRepository.count(),
                raceRepository.countByStatusIn(activeStatuses),
                participantRepository.count(),
                gpsLocationRepository.countDistinctParticipantsActiveSince(liveSince),
                raceRepository.countByRaceDateBetween(today.plusDays(1), today.plusDays(30)),
                checkpointRecordRepository.countFinishRecordsSince(dayStart)
            );
        }

        if (authentication == null || authentication.getName() == null) {
            throw new org.springframework.security.access.AccessDeniedException("Dashboard access requires authentication");
        }
        String email = authentication.getName();
        return new DashboardStatsDto(
            raceRepository.countManagedBy(email),
            raceRepository.countManagedByStatusIn(email, activeStatuses),
            participantRepository.countInRacesManagedBy(email),
            gpsLocationRepository.countDistinctParticipantsActiveSinceInRacesManagedBy(email, liveSince),
            raceRepository.countManagedByRaceDateBetween(email, today.plusDays(1), today.plusDays(30)),
            checkpointRecordRepository.countFinishRecordsSinceInRacesManagedBy(email, dayStart)
        );
    }
}
