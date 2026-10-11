package com.racetracker.service.impl;

import com.racetracker.config.RaceTrackerProperties;
import com.racetracker.dto.publictracking.HeatCellDto;
import com.racetracker.dto.publictracking.PublicAthleteTrackingResponse;
import com.racetracker.dto.publictracking.PublicParticipantTrackResponse;
import com.racetracker.dto.publictracking.PublicRaceAthleteSummaryResponse;
import com.racetracker.dto.publictracking.PublicRaceHubResponse;
import com.racetracker.dto.publictracking.PublicRaceLiveFeedResponse;
import com.racetracker.dto.publictracking.TrackPointDto;
import com.racetracker.dto.tracking.CheckpointSplitDto;
import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.exception.ConflictException;
import com.racetracker.model.CheckpointRecord;
import com.racetracker.model.GpsLocation;
import com.racetracker.model.Participant;
import com.racetracker.model.Race;
import com.racetracker.repository.CheckpointRecordRepository;
import com.racetracker.repository.GpsLocationRepository;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.RaceRepository;
import com.racetracker.repository.RaceFeedRevisionRepository;
import com.racetracker.service.PublicTrackingService;
import com.racetracker.service.GpsDriftFilter;
import com.racetracker.service.LastCheckpointNameCache;
import io.micrometer.observation.annotation.Observed;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.stream.Collectors;

@Service
public class PublicTrackingServiceImpl implements PublicTrackingService {

    private static final int HUB_LEADERBOARD_LIMIT = 10;
    private static final int DEFAULT_FEED_LIMIT = 25;
    private static final int MAX_FEED_LIMIT = 100;

    private final RaceRepository raceRepository;
    private final ParticipantRepository participantRepository;
    private final GpsLocationRepository gpsLocationRepository;
    private final CheckpointRecordRepository checkpointRecordRepository;
    private final RaceFeedRevisionRepository feedRevisionRepository;
    private final LastCheckpointNameCache lastCheckpointNameCache;
    private final RaceTrackerProperties raceTrackerProperties;

    public PublicTrackingServiceImpl(
        RaceRepository raceRepository,
        ParticipantRepository participantRepository,
        GpsLocationRepository gpsLocationRepository,
        CheckpointRecordRepository checkpointRecordRepository,
        RaceFeedRevisionRepository feedRevisionRepository,
        LastCheckpointNameCache lastCheckpointNameCache,
        RaceTrackerProperties raceTrackerProperties
    ) {
        this.raceRepository = raceRepository;
        this.participantRepository = participantRepository;
        this.gpsLocationRepository = gpsLocationRepository;
        this.checkpointRecordRepository = checkpointRecordRepository;
        this.feedRevisionRepository = feedRevisionRepository;
        this.lastCheckpointNameCache = lastCheckpointNameCache;
        this.raceTrackerProperties = raceTrackerProperties;
    }

    @Override
    @Observed(name = "tracking.public.race_hub")
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
        response.setResultsPublished(race.getResultsPublishedAt() != null);
        response.setRegistrationOpens(race.getRegistrationOpens() != null ? race.getRegistrationOpens().toString() : null);
        response.setRegistrationCloses(race.getRegistrationCloses() != null ? race.getRegistrationCloses().toString() : null);
        response.setMaxParticipants(race.getMaxParticipants());
        response.setRegistrationFee(race.getRegistrationFee());
        response.setCurrency(race.getCurrency());
        response.setIsPublicRegistration(race.getIsPublicRegistration());
        response.setRequireApproval(race.getRequireApproval());
        response.setWaitlistEnabled(race.getWaitlistEnabled());
        response.setRequireWaiver(race.getRequireWaiver());
        response.setWaiverVersion(race.getWaiverVersion());
        response.setWaiverText(race.getWaiverText());
        response.setParticipantCount((int) participantRepository.countByRaceId(raceId));
        response.setCategories(race.getCategories() == null ? List.of() : race.getCategories().stream()
            .map(category -> {
                com.racetracker.dto.race.RaceCategoryDto dto = new com.racetracker.dto.race.RaceCategoryDto();
                dto.setId(category.getId());
                dto.setRaceId(raceId);
                dto.setName(category.getName());
                dto.setDescription(category.getDescription());
                dto.setMinAge(category.getMinAge());
                dto.setMaxAge(category.getMaxAge());
                dto.setGender(category.getGender());
                dto.setBibPrefix(category.getBibPrefix());
                dto.setMaxParticipants(category.getMaxParticipants());
                dto.setRegistrationFee(category.getRegistrationFee());
                return dto;
            })
            .toList());
        response.setLeaderboard(getLiveFeed(raceId, null, null, HUB_LEADERBOARD_LIMIT).getItems().stream()
            .sorted(Comparator
                .comparing((PublicRaceAthleteSummaryResponse row) -> row.getDistanceRemaining() == null
                    ? BigDecimal.valueOf(Double.MAX_VALUE) : row.getDistanceRemaining())
                .thenComparing(row -> row.getParticipantName() == null ? "" : row.getParticipantName()))
            .toList());
        return response;
    }

    @Override
    @Observed(name = "tracking.public.live_athletes")
    @Transactional(readOnly = true)
    public List<PublicRaceAthleteSummaryResponse> getLiveAthletes(UUID raceId, String query) {
        getVisibleRace(raceId);

        if (!hasText(query)) {
            return getLiveFeed(raceId, null, null, MAX_FEED_LIMIT).getItems();
        }

        Page<Participant> participants = participantRepository.searchPublicByRaceId(
            raceId,
            query.trim(),
            PageRequest.of(0, MAX_FEED_LIMIT)
        );

        return buildAthleteSummaries(raceId, participants.getContent(), false);
    }

    @Override
    @Observed(name = "tracking.public.live_feed")
    @Transactional(readOnly = true)
    public PublicRaceLiveFeedResponse getLiveFeed(UUID raceId, String query, String cursor, int limit) {
        getVisibleRace(raceId);

        int boundedLimit = Math.max(1, Math.min(limit <= 0 ? DEFAULT_FEED_LIMIT : limit, MAX_FEED_LIMIT));
        long revision = feedRevisionRepository.currentRevision(raceId);
        FeedCursor position = decodeCursor(cursor, raceId, revision);
        Pageable pageable = PageRequest.of(0, boundedLimit + 1);
        List<Participant> fetched = participantRepository.findPublicLiveAfter(
            raceId,
            hasText(query) ? query.trim() : "",
            position == null ? null : position.bibNumber(),
            position == null ? null : position.registeredAt(),
            position == null ? null : position.participantId(),
            pageable
        );
        boolean hasNext = fetched.size() > boundedLimit;
        List<Participant> participants = hasNext ? fetched.subList(0, boundedLimit) : fetched;

        PublicRaceLiveFeedResponse response = new PublicRaceLiveFeedResponse();
        response.setItems(buildAthleteSummaries(raceId, participants, false));
        response.setNextCursor(hasNext && !participants.isEmpty()
            ? encodeCursor(raceId, revision, participants.get(participants.size() - 1))
            : null);
        response.setRefreshedAt(OffsetDateTime.now());
        return response;
    }

    @Override
    @Observed(name = "tracking.public.athlete_detail")
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

    @Override
    @Observed(name = "tracking.public.athlete_track")
    @Transactional(readOnly = true)
    public PublicParticipantTrackResponse getAthleteTrack(UUID raceId, UUID participantId) {
        getVisibleRace(raceId);
        Participant participant = participantRepository.findByRaceIdAndId(raceId, participantId)
            .orElseThrow(() -> new ResourceNotFoundException("Participant not found"));

        if (!Boolean.TRUE.equals(participant.getIsTrackingEnabled())) {
            return new PublicParticipantTrackResponse(List.of());
        }

        List<GpsLocation> locations = gpsLocationRepository.findByParticipantIdAndRaceIdOrderByRecordedAtAsc(participantId, raceId)
            .stream()
            .filter(location -> !Boolean.TRUE.equals(location.getFiltered()))
            .toList();

        int maxPoints = Math.max(2, raceTrackerProperties.getTracking().getMaxTrackPoints());
        int stride = Math.max(1, (int) Math.ceil(locations.size() / (double) maxPoints));
        List<TrackPointDto> points = new ArrayList<>();
        for (int i = 0; i < locations.size(); i += stride) {
            GpsLocation location = locations.get(i);
            points.add(new TrackPointDto(
                GpsDriftFilter.effectiveLatitude(location),
                GpsDriftFilter.effectiveLongitude(location),
                location.getRecordedAt() != null ? location.getRecordedAt().toString() : null,
                location.getSpeed()
            ));
        }
        GpsLocation last = locations.isEmpty() ? null : locations.get(locations.size() - 1);
        if (last != null && (points.isEmpty() || !points.get(points.size() - 1).getTime().equals(last.getRecordedAt().toString()))) {
            points.add(new TrackPointDto(
                GpsDriftFilter.effectiveLatitude(last),
                GpsDriftFilter.effectiveLongitude(last),
                last.getRecordedAt() != null ? last.getRecordedAt().toString() : null,
                last.getSpeed()
            ));
        }
        return new PublicParticipantTrackResponse(points);
    }

    @Override
    @Observed(name = "tracking.public.heatmap")
    @Transactional(readOnly = true)
    public List<HeatCellDto> getRaceHeatmap(UUID raceId, OffsetDateTime from, OffsetDateTime to, Integer scale) {
        getVisibleRace(raceId);
        int boundedScale = scale == null ? raceTrackerProperties.getTracking().getHeatmapScale()
            : Math.max(1, Math.min(scale, 6));
        return gpsLocationRepository.heatCells(raceId, from, to, boundedScale).stream()
            .map(cell -> new HeatCellDto(cell.getLatitude(), cell.getLongitude(), cell.getWeight()))
            .toList();
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
        Map<UUID, GpsLocation> latestByParticipant = gpsLocationRepository.findLatestByRaceIdAndParticipantIds(raceId, participantIds)
            .stream()
            .collect(Collectors.toMap(location -> location.getParticipant().getId(), location -> location, (left, right) -> left));
        Map<UUID, List<CheckpointRecord>> recordsByParticipant = checkpointRecordRepository
            .findByRaceIdAndParticipantIdInOrderByRecordedAtAsc(raceId, participantIds)
            .stream()
            .collect(Collectors.groupingBy(record -> record.getParticipant().getId()));

        List<PublicRaceAthleteSummaryResponse> summaries = new ArrayList<>();
        for (Participant participant : participants) {
            GpsLocation latest = latestByParticipant.get(participant.getId());
            List<CheckpointRecord> records = recordsByParticipant.getOrDefault(participant.getId(), List.of());
            if (liveOnly && !isPubliclyRelevant(participant, latest)) {
                continue;
            }
            summaries.add(buildAthleteSummary(participant, latest, records, raceId));
        }

        return List.copyOf(summaries);
    }

    private boolean isPubliclyRelevant(Participant participant, GpsLocation latest) {
        return participant.getStatus() == Participant.ParticipantStatus.STARTED
            && latest != null;
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
        return lastCheckpointNameCache.getName(participant.getId(), raceId);
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String encodeCursor(UUID raceId, long revision, Participant participant) {
        String bib = participant.getBibNumber() == null ? "" : participant.getBibNumber();
        OffsetDateTime registeredAt = participant.getRegisteredAt() == null
            ? participant.getCreatedAt()
            : participant.getRegisteredAt();
        String encodedBib = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(bib.getBytes(StandardCharsets.UTF_8));
        String payload = String.join("|", "v2", raceId.toString(), Long.toString(revision), encodedBib,
            registeredAt.toString(), participant.getId().toString());
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    private FeedCursor decodeCursor(String cursor, UUID expectedRaceId, long currentRevision) {
        if (!hasText(cursor)) {
            return null;
        }

        try {
            if (cursor.length() > 2048) throw new IllegalArgumentException("Cursor is too long");
            String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] fields = decoded.split("\\|", -1);
            if (fields.length != 6 || !"v2".equals(fields[0])) throw new IllegalArgumentException("Unsupported cursor version");
            UUID raceId = UUID.fromString(fields[1]);
            long revision = Long.parseLong(fields[2]);
            String bib = new String(Base64.getUrlDecoder().decode(fields[3]), StandardCharsets.UTF_8);
            OffsetDateTime registeredAt = OffsetDateTime.parse(fields[4]);
            UUID participantId = UUID.fromString(fields[5]);
            if (!expectedRaceId.equals(raceId)) throw new IllegalArgumentException("Cursor belongs to another race");
            if (revision != currentRevision) {
                throw new ConflictException("Live feed changed; restart pagination from the first page");
            }
            return new FeedCursor(bib, registeredAt, participantId);
        } catch (ConflictException ex) {
            throw ex;
        } catch (IllegalArgumentException ex) {
            throw new com.racetracker.exception.ApiException("Invalid live feed cursor");
        } catch (RuntimeException ex) {
            throw new com.racetracker.exception.ApiException("Invalid live feed cursor");
        }
    }

    private record FeedCursor(String bibNumber, OffsetDateTime registeredAt, UUID participantId) { }

    private String formatDuration(Duration duration) {
        long totalSeconds = duration.getSeconds();
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
}
