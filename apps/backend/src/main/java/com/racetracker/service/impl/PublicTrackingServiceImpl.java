package com.racetracker.service.impl;

import com.racetracker.dto.publictracking.PublicAthleteTrackingResponse;
import com.racetracker.dto.publictracking.PublicRaceAthleteSummaryResponse;
import com.racetracker.dto.publictracking.PublicRaceHubResponse;
import com.racetracker.dto.tracking.CheckpointSplitDto;
import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.model.CheckpointRecord;
import com.racetracker.model.GpsLocation;
import com.racetracker.model.Participant;
import com.racetracker.model.Race;
import com.racetracker.repository.CheckpointRecordRepository;
import com.racetracker.repository.GpsLocationRepository;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.RaceRepository;
import com.racetracker.service.PublicTrackingService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PublicTrackingServiceImpl implements PublicTrackingService {

    private static final int HUB_LEADERBOARD_LIMIT = 10;
    private static final EnumSet<Participant.ParticipantStatus> PUBLICLY_RELEVANT_STATUSES = EnumSet.of(
        Participant.ParticipantStatus.STARTED,
        Participant.ParticipantStatus.FINISHED,
        Participant.ParticipantStatus.DNF,
        Participant.ParticipantStatus.DISQUALIFIED
    );

    private final RaceRepository raceRepository;
    private final ParticipantRepository participantRepository;
    private final GpsLocationRepository gpsLocationRepository;
    private final CheckpointRecordRepository checkpointRecordRepository;

    public PublicTrackingServiceImpl(
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
    public PublicRaceHubResponse getRaceHub(UUID raceId) {
        Race race = getVisibleRace(raceId);

        PublicRaceHubResponse response = new PublicRaceHubResponse();
        response.setRaceId(race.getId());
        response.setRaceName(race.getName());
        response.setStatus(race.getStatus() != null ? race.getStatus().name() : null);
        response.setRaceDate(race.getRaceDate() != null ? race.getRaceDate().toString() : null);
        response.setCity(race.getCity());
        response.setCountry(race.getCountry());
        response.setDistanceKm(race.getDistanceKm());
        response.setBannerImageUrl(race.getBannerImageUrl());
        response.setLogoUrl(race.getLogoUrl());
        response.setLeaderboard(getLiveAthletes(raceId, null).stream().limit(HUB_LEADERBOARD_LIMIT).toList());
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PublicRaceAthleteSummaryResponse> getLiveAthletes(UUID raceId, String query) {
        getVisibleRace(raceId);

        List<Participant> participants = hasText(query)
            ? participantRepository.searchPublicByRaceId(raceId, query.trim())
            : participantRepository.findByRaceId(raceId);

        return buildAthleteSummaries(raceId, participants, !hasText(query));
    }

    @Override
    @Transactional(readOnly = true)
    public PublicAthleteTrackingResponse getAthleteTracking(UUID raceId, UUID participantId) {
        getVisibleRace(raceId);
        Participant participant = participantRepository.findByRaceIdAndId(raceId, participantId)
            .orElseThrow(() -> new ResourceNotFoundException("Participant not found"));

        PublicRaceAthleteSummaryResponse summary = buildAthleteSummaries(raceId, List.of(participant), false).stream()
            .findFirst()
            .orElseGet(() -> buildAthleteSummary(participant, null, List.of(), raceId));

        PublicAthleteTrackingResponse response = new PublicAthleteTrackingResponse();
        response.setParticipantId(summary.getParticipantId());
        response.setParticipantName(summary.getParticipantName());
        response.setBibNumber(summary.getBibNumber());
        response.setStatus(summary.getStatus());
        response.setLastCheckpoint(summary.getLastCheckpoint());
        response.setElapsedTime(summary.getElapsedTime());
        response.setAveragePace(summary.getAveragePace());
        response.setPredictedFinishTime(summary.getPredictedFinishTime());
        response.setDistanceRemaining(summary.getDistanceRemaining());

        List<CheckpointRecord> records = checkpointRecordRepository.findByParticipantIdAndRaceIdOrderByRecordedAtAsc(participantId, raceId);
        response.setSplits(buildSplits(participant, records));
        return response;
    }

    @Cacheable(value = "lastCheckpoint", key = "#participantId + '-' + #raceId")
    public String getLastCheckpointName(UUID participantId, UUID raceId) {
        return checkpointRecordRepository.findTopByParticipantIdAndRaceIdOrderByRecordedAtDesc(participantId, raceId)
            .map(record -> record.getCheckpoint() != null ? record.getCheckpoint().getName() : null)
            .orElse(null);
    }

    private Race getVisibleRace(UUID raceId) {
        Race race = raceRepository.findById(raceId)
            .orElseThrow(() -> new ResourceNotFoundException("Race not found"));

        if (race.getStatus() == Race.RaceStatus.DRAFT) {
            throw new ResourceNotFoundException("Race not found");
        }

        return race;
    }

    private List<PublicRaceAthleteSummaryResponse> buildAthleteSummaries(
        UUID raceId,
        List<Participant> participants,
        boolean liveOnly
    ) {
        if (participants.isEmpty()) {
            return List.of();
        }

        List<UUID> participantIds = participants.stream().map(Participant::getId).toList();
        Map<UUID, List<CheckpointRecord>> recordsByParticipant = checkpointRecordRepository
            .findByRaceIdAndParticipantIdInOrderByRecordedAtAsc(raceId, participantIds)
            .stream()
            .collect(Collectors.groupingBy(record -> record.getParticipant().getId()));

        List<PublicRaceAthleteSummaryResponse> summaries = new ArrayList<>();
        for (Participant participant : participants) {
            GpsLocation latest = gpsLocationRepository
                .findTopByParticipantIdAndRaceIdOrderByRecordedAtDesc(participant.getId(), raceId)
                .orElse(null);
            List<CheckpointRecord> records = recordsByParticipant.getOrDefault(participant.getId(), List.of());
            if (liveOnly && !isPubliclyRelevant(participant, latest, records)) {
                continue;
            }
            summaries.add(buildAthleteSummary(participant, latest, records, raceId));
        }

        return summaries.stream()
            .sorted(Comparator
                .comparing((PublicRaceAthleteSummaryResponse row) -> row.getDistanceRemaining() == null ? BigDecimal.valueOf(Double.MAX_VALUE) : row.getDistanceRemaining())
                .thenComparing(row -> row.getParticipantName() == null ? "" : row.getParticipantName()))
            .toList();
    }

    private boolean isPubliclyRelevant(Participant participant, GpsLocation latest, List<CheckpointRecord> records) {
        return latest != null
            || !records.isEmpty()
            || (participant.getStatus() != null && PUBLICLY_RELEVANT_STATUSES.contains(participant.getStatus()));
    }

    private PublicRaceAthleteSummaryResponse buildAthleteSummary(
        Participant participant,
        GpsLocation latest,
        List<CheckpointRecord> records,
        UUID raceId
    ) {
        PublicRaceAthleteSummaryResponse response = new PublicRaceAthleteSummaryResponse();
        response.setParticipantId(participant.getId());
        response.setParticipantName(participant.getFullName());
        response.setBibNumber(participant.getBibNumber());
        response.setStatus(participant.getStatus() != null ? participant.getStatus().name() : null);
        response.setLastCheckpoint(resolveLastCheckpoint(participant, records, raceId));

        OffsetDateTime latestTimestamp = latest != null ? latest.getRecordedAt() : null;
        OffsetDateTime start = participant.getCheckedInAt() != null ? participant.getCheckedInAt() : participant.getRegisteredAt();
        Duration elapsed = null;
        if (start != null && latestTimestamp != null && latestTimestamp.isAfter(start)) {
            elapsed = Duration.between(start, latestTimestamp);
            response.setElapsedTime(formatDuration(elapsed));
        }

        BigDecimal distanceCovered = participant.getTotalDistance() != null ? participant.getTotalDistance() : BigDecimal.ZERO;
        Race race = participant.getRace();
        if (race != null && race.getDistanceKm() != null) {
            BigDecimal remaining = race.getDistanceKm().subtract(distanceCovered).max(BigDecimal.ZERO);
            response.setDistanceRemaining(remaining);

            if (elapsed != null && distanceCovered.compareTo(BigDecimal.ZERO) > 0) {
                double paceMinutes = (elapsed.toSeconds() / 60.0) / distanceCovered.doubleValue();
                int minutes = (int) paceMinutes;
                int seconds = (int) ((paceMinutes - minutes) * 60);
                response.setAveragePace(String.format("%d:%02d", minutes, seconds));

                if (latestTimestamp != null && paceMinutes > 0) {
                    double avgSpeedKmH = 60.0 / paceMinutes;
                    double remainingHours = remaining.doubleValue() / avgSpeedKmH;
                    OffsetDateTime eta = latestTimestamp.plus(Duration.ofMinutes((long) (remainingHours * 60)));
                    response.setPredictedFinishTime(eta.toString());
                }
            }
        }

        return response;
    }

    private List<CheckpointSplitDto> buildSplits(Participant participant, List<CheckpointRecord> records) {
        OffsetDateTime start = participant.getCheckedInAt() != null ? participant.getCheckedInAt() : participant.getRegisteredAt();
        return records.stream()
            .map(record -> {
                String time = "---";
                if (start != null && record.getRecordedAt() != null) {
                    time = formatDuration(Duration.between(start, record.getRecordedAt()));
                }
                return new CheckpointSplitDto(
                    record.getCheckpoint() != null ? record.getCheckpoint().getName() : null,
                    time
                );
            })
            .toList();
    }

    private String resolveLastCheckpoint(Participant participant, List<CheckpointRecord> records, UUID raceId) {
        if (!records.isEmpty()) {
            CheckpointRecord record = records.get(records.size() - 1);
            return record.getCheckpoint() != null ? record.getCheckpoint().getName() : null;
        }
        return getLastCheckpointName(participant.getId(), raceId);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String formatDuration(Duration duration) {
        long totalSeconds = duration.getSeconds();
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
}
