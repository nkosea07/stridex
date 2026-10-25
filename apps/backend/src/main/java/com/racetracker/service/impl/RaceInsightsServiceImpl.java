package com.racetracker.service.impl;

import com.racetracker.dto.insights.ParticipantAnomalyDto;
import com.racetracker.dto.insights.RaceInsightsSummaryDto;
import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.model.Participant;
import com.racetracker.model.Race;
import com.racetracker.repository.CheckpointRecordRepository;
import com.racetracker.repository.GpsLocationRepository;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.RaceRepository;
import com.racetracker.service.RaceInsightsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Derived, compute-on-read race insights: finish-time projections and simple integrity
 * anomalies (drift-filtered GPS points, missed cutoffs, disqualified/DNF status).
 */
@Service
public class RaceInsightsServiceImpl implements RaceInsightsService {

    private final ParticipantRepository participantRepository;
    private final RaceRepository raceRepository;
    private final GpsLocationRepository gpsLocationRepository;
    private final CheckpointRecordRepository checkpointRecordRepository;

    public RaceInsightsServiceImpl(
        ParticipantRepository participantRepository,
        RaceRepository raceRepository,
        GpsLocationRepository gpsLocationRepository,
        CheckpointRecordRepository checkpointRecordRepository
    ) {
        this.participantRepository = participantRepository;
        this.raceRepository = raceRepository;
        this.gpsLocationRepository = gpsLocationRepository;
        this.checkpointRecordRepository = checkpointRecordRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public RaceInsightsSummaryDto summary(UUID raceId) {
        Race race = requireRace(raceId);
        List<Participant> participants = participantRepository.findByRaceId(raceId);
        OffsetDateTime now = OffsetDateTime.now();
        BigDecimal raceDistance = race.getDistanceKm();

        double paceSum = 0;
        int paceSamples = 0;
        OffsetDateTime predictedLeader = null;

        for (Participant participant : participants) {
            if (participant.getStatus() != Participant.ParticipantStatus.STARTED) {
                continue;
            }
            OffsetDateTime start = participant.getCheckedInAt() != null
                ? participant.getCheckedInAt() : participant.getRegisteredAt();
            BigDecimal distance = participant.getTotalDistance() != null
                ? participant.getTotalDistance() : BigDecimal.ZERO;
            if (start == null || distance.signum() <= 0) {
                continue;
            }
            long elapsedSeconds = Math.max(Duration.between(start, now).getSeconds(), 1);
            double paceSecondsPerKm = elapsedSeconds / distance.doubleValue();
            paceSum += paceSecondsPerKm;
            paceSamples++;

            if (raceDistance != null && raceDistance.doubleValue() > 0) {
                BigDecimal remaining = raceDistance.subtract(distance).max(BigDecimal.ZERO);
                OffsetDateTime eta = now.plusSeconds((long) (remaining.doubleValue() * paceSecondsPerKm));
                if (predictedLeader == null || eta.isBefore(predictedLeader)) {
                    predictedLeader = eta;
                }
            }
        }

        RaceInsightsSummaryDto dto = new RaceInsightsSummaryDto();
        dto.setTotalParticipants(participants.size());
        dto.setLiveParticipants(participants.stream()
            .filter(p -> p.getStatus() == Participant.ParticipantStatus.STARTED).count());
        dto.setAveragePaceSecondsPerKm(paceSamples == 0 ? 0 : round2(paceSum / paceSamples));
        dto.setPredictedLeaderFinishTime(predictedLeader != null ? predictedLeader.toString() : null);
        List<ParticipantAnomalyDto> anomalies = anomalies(raceId);
        dto.setAnomalyCount(anomalies.size());
        dto.setAtRiskParticipants(anomalies.size());
        dto.setDropoutRiskPercent(participants.isEmpty() ? 0
            : round2((anomalies.size() * 100.0) / participants.size()));
        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ParticipantAnomalyDto> anomalies(UUID raceId) {
        requireRace(raceId);
        Map<UUID, Long> filteredByParticipant = toCountMap(gpsLocationRepository.countFilteredByRace(raceId));
        Map<UUID, Long> cutoffByParticipant = toCountMap(checkpointRecordRepository.countCutoffMissedByRace(raceId));

        List<ParticipantAnomalyDto> anomalies = new ArrayList<>();
        for (Participant participant : participantRepository.findByRaceId(raceId)) {
            long filtered = filteredByParticipant.getOrDefault(participant.getId(), 0L);
            long missedCutoffs = cutoffByParticipant.getOrDefault(participant.getId(), 0L);
            boolean disqualified = participant.getStatus() == Participant.ParticipantStatus.DISQUALIFIED
                || participant.getStatus() == Participant.ParticipantStatus.DNF;
            double score = filtered * 0.5 + missedCutoffs * 2.0 + (disqualified ? 1.0 : 0.0);
            if (score <= 0) {
                continue;
            }
            ParticipantAnomalyDto dto = new ParticipantAnomalyDto();
            dto.setParticipantId(participant.getId());
            dto.setParticipantName(participant.getFullName());
            dto.setBibNumber(participant.getBibNumber());
            dto.setStatus(participant.getStatus() != null ? participant.getStatus().name() : null);
            dto.setFilteredGpsPoints(filtered);
            dto.setMissedCutoffs(missedCutoffs);
            dto.setAnomalyScore(round2(score));
            anomalies.add(dto);
        }
        anomalies.sort(Comparator.comparingDouble(ParticipantAnomalyDto::getAnomalyScore).reversed());
        return anomalies;
    }

    private Map<UUID, Long> toCountMap(List<Object[]> rows) {
        Map<UUID, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            if (row[0] instanceof UUID id && row[1] instanceof Number count) {
                map.put(id, count.longValue());
            }
        }
        return map;
    }

    private Race requireRace(UUID raceId) {
        return raceRepository.findById(raceId)
            .orElseThrow(() -> new ResourceNotFoundException("Race not found"));
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
