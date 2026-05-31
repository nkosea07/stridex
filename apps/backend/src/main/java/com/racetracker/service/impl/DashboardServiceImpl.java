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
        long totalRaces = raceRepository.count();

        long activeRaces = raceRepository.countByStatusIn(List.of(
            Race.RaceStatus.IN_PROGRESS,
            Race.RaceStatus.REGISTRATION_OPEN
        ));

        long totalParticipants = participantRepository.count();

        OffsetDateTime liveSince = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(10);
        long liveTracking = gpsLocationRepository.countDistinctParticipantsActiveSince(liveSince);

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        long upcomingRaces = raceRepository.countByRaceDateBetween(today.plusDays(1), today.plusDays(30));

        OffsetDateTime dayStart = today.atStartOfDay().atOffset(ZoneOffset.UTC);
        long completedToday = checkpointRecordRepository.countFinishRecordsSince(dayStart);

        return new DashboardStatsDto(
            totalRaces,
            activeRaces,
            totalParticipants,
            liveTracking,
            upcomingRaces,
            completedToday
        );
    }
}
