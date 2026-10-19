package com.racetracker.service.impl;

import com.racetracker.dto.tracking.*;
import com.racetracker.exception.ApiException;
import com.racetracker.exception.ConflictException;
import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.exception.UnauthorizedException;
import com.racetracker.model.*;
import com.racetracker.repository.*;
import com.racetracker.service.GeoTelemetryService;
import com.racetracker.service.GpsDriftFilter;
import com.racetracker.service.CheckpointCutoff;
import com.racetracker.service.LastCheckpointNameCache;
import com.racetracker.service.LiveSummaryCacheInvalidator;
import com.racetracker.service.SpectatorAlertService;
import com.racetracker.service.TrackingService;
import com.racetracker.security.CapabilityAuthorizationService;
import com.racetracker.websocket.TrackingLivePublisher;
import io.micrometer.observation.annotation.Observed;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TrackingServiceImpl implements TrackingService {

    private final RaceRepository raceRepository;
    private final ParticipantRepository participantRepository;
    private final UserRepository userRepository;
    private final GpsLocationRepository gpsLocationRepository;
    private final CheckpointRepository checkpointRepository;
    private final CheckpointRecordRepository checkpointRecordRepository;
    private final TrackingLivePublisher trackingLivePublisher;
    private final GeoTelemetryService geoTelemetryService;
    private final GpsDriftFilter gpsDriftFilter;
    private final LiveSummaryCacheInvalidator liveSummaryCacheInvalidator;
    private final LastCheckpointNameCache lastCheckpointNameCache;
    private final SpectatorAlertService spectatorAlertService;
    private final CapabilityAuthorizationService capabilityAuthorizationService;

    public TrackingServiceImpl(
        RaceRepository raceRepository,
        ParticipantRepository participantRepository,
        UserRepository userRepository,
        GpsLocationRepository gpsLocationRepository,
        CheckpointRepository checkpointRepository,
        CheckpointRecordRepository checkpointRecordRepository,
        TrackingLivePublisher trackingLivePublisher,
        GeoTelemetryService geoTelemetryService,
        GpsDriftFilter gpsDriftFilter,
        LiveSummaryCacheInvalidator liveSummaryCacheInvalidator,
        LastCheckpointNameCache lastCheckpointNameCache,
        SpectatorAlertService spectatorAlertService,
        CapabilityAuthorizationService capabilityAuthorizationService
    ) {
        this.raceRepository = raceRepository;
        this.participantRepository = participantRepository;
        this.userRepository = userRepository;
        this.gpsLocationRepository = gpsLocationRepository;
        this.checkpointRepository = checkpointRepository;
        this.checkpointRecordRepository = checkpointRecordRepository;
        this.trackingLivePublisher = trackingLivePublisher;
        this.geoTelemetryService = geoTelemetryService;
        this.gpsDriftFilter = gpsDriftFilter;
        this.liveSummaryCacheInvalidator = liveSummaryCacheInvalidator;
        this.lastCheckpointNameCache = lastCheckpointNameCache;
        this.spectatorAlertService = spectatorAlertService;
        this.capabilityAuthorizationService = capabilityAuthorizationService;
    }

    @Override
    @Observed(name = "tracking.ingest.location")
    @Transactional
    public void recordLocation(UUID raceId, GpsLocationRequest request) {
        capabilityAuthorizationService.require(com.racetracker.model.UserCapabilityType.ATHLETE);
        Participant participant = resolveCurrentParticipant(raceId);
        GpsLocation latest = saveLocation(participant, request);
        trackingLivePublisher.publishLocationUpdate(raceId, buildLiveTrackingResponse(participant, latest, raceId));
    }

    @Override
    @Observed(name = "tracking.ingest.location_batch")
    @Transactional
    public void recordLocationsBatch(UUID raceId, GpsLocationBatchRequest request) {
        capabilityAuthorizationService.require(com.racetracker.model.UserCapabilityType.ATHLETE);
        Participant participant = resolveCurrentParticipant(raceId);
        if (request == null || request.getLocations() == null || request.getLocations().isEmpty()) {
            return;
        }

        BigDecimal batchDistance = BigDecimal.ZERO;
        GpsLocation lastAccepted = gpsLocationRepository
            .findTopByParticipantIdAndRaceIdOrderByRecordedAtDesc(participant.getId(), raceId)
            .orElse(null);

        for (GpsLocationRequest item : request.getLocations()) {
            GpsLocation location = buildLocation(participant, item);
            GpsDriftFilter.FilterResult result = gpsDriftFilter.filter(
                lastAccepted, item.getLatitude(), item.getLongitude(), item.getAccuracy(), location.getRecordedAt());
            applyFilter(location, result);
            GpsLocation saved = gpsLocationRepository.save(location);

            if (!result.filtered()) {
                if (result.distanceKm() > 0) {
                    batchDistance = batchDistance.add(BigDecimal.valueOf(result.distanceKm()));
                }
                lastAccepted = saved;
            }
        }

        if (batchDistance.compareTo(BigDecimal.ZERO) > 0) {
            participantRepository.incrementTotalDistance(participant.getId(), batchDistance);
            participant = participantRepository.findById(participant.getId()).orElse(participant);
        }

        if (lastAccepted != null) {
            trackingLivePublisher.publishLocationUpdate(raceId, buildLiveTrackingResponse(participant, lastAccepted, raceId));
        }
    }

    @Override
    @Observed(name = "tracking.ingest.checkpoint")
    @Transactional
    public void recordCheckpoint(UUID raceId, TrackingCheckpointRequest request) {
        capabilityAuthorizationService.require(com.racetracker.model.UserCapabilityType.ATHLETE);
        if (request == null || request.getCheckpointId() == null) {
            throw new ApiException("checkpointId is required");
        }

        Participant participant = resolveCurrentParticipant(raceId);
        Checkpoint checkpoint = checkpointRepository.findByRaceIdAndId(raceId, request.getCheckpointId())
            .orElseThrow(() -> new ResourceNotFoundException("Checkpoint not found"));

        if (checkpointRecordRepository.findByParticipantIdAndCheckpointId(participant.getId(), checkpoint.getId()).isPresent()) {
            throw new ConflictException("Checkpoint already recorded");
        }

        OffsetDateTime recordedAt = parseTimestamp(request.getTimestamp());
        boolean cutoffMissed = evaluateCutoff(checkpoint, participant, raceId, recordedAt);

        CheckpointRecord record = new CheckpointRecord();
        record.setRace(checkpoint.getRace());
        record.setCheckpoint(checkpoint);
        record.setParticipant(participant);
        record.setRecordedAt(recordedAt);
        record.setRecordMethod(parseRecordMethod(request.getMethod()));
        record.setRecordedBy(resolveCurrentUser());
        record.setIsVerified(true);
        record.setIsCutoffMissed(cutoffMissed);

        checkpointRecordRepository.save(record);
        
        liveSummaryCacheInvalidator.evictLastCheckpointAfterCommit(participant.getId(), raceId);

        Participant.ParticipantStatus previousStatus = participant.getStatus();
        if (cutoffMissed) {
            participant.setStatus(Participant.ParticipantStatus.DNF);
        } else if (checkpoint.getCheckpointType() == Checkpoint.CheckpointType.FINISH) {
            participant.setStatus(Participant.ParticipantStatus.FINISHED);
        } else if (participant.getStatus() == Participant.ParticipantStatus.REGISTERED
            || participant.getStatus() == Participant.ParticipantStatus.CONFIRMED
            || participant.getStatus() == Participant.ParticipantStatus.CHECKED_IN) {
            participant.setStatus(Participant.ParticipantStatus.STARTED);
        }
        participantRepository.save(participant);

        spectatorAlertService.onCheckpoint(participant, checkpoint);
        if (!cutoffMissed
            && participant.getStatus() == Participant.ParticipantStatus.FINISHED
            && previousStatus != Participant.ParticipantStatus.FINISHED) {
            spectatorAlertService.onFinished(participant);
        } else if (!cutoffMissed
            && participant.getStatus() == Participant.ParticipantStatus.STARTED
            && previousStatus != Participant.ParticipantStatus.STARTED) {
            spectatorAlertService.onStarted(participant);
        }
    }

    private boolean evaluateCutoff(Checkpoint checkpoint, Participant participant, UUID raceId, OffsetDateTime recordedAt) {
        if (checkpoint.getCutoffTime() == null) {
            return false;
        }
        List<CheckpointRecord> existing = checkpointRecordRepository
            .findByParticipantIdAndRaceIdOrderByRecordedAtAsc(participant.getId(), raceId);
        OffsetDateTime start = CheckpointCutoff.resolveStart(participant, existing);
        return CheckpointCutoff.isMissed(checkpoint.getCutoffTime(), start, recordedAt);
    }

    @Override
    @Observed(name = "tracking.live.aggregate")
    @Transactional(readOnly = true)
    public List<LiveTrackingResponse> getLiveData(UUID raceId) {
        verifyRaceExists(raceId);
        List<Participant> participants = participantRepository.findByRaceId(raceId);
        List<LiveTrackingResponse> results = new ArrayList<>();

        for (Participant p : participants) {
            Optional<GpsLocation> latest = gpsLocationRepository.findTopByParticipantIdAndRaceIdOrderByRecordedAtDesc(p.getId(), raceId);
            if (latest.isPresent()) {
                results.add(buildLiveTrackingResponse(p, latest.get(), raceId));
            }
        }
        return results;
    }

    @Override
    @Observed(name = "tracking.live.participant_track")
    @Transactional(readOnly = true)
    public ParticipantTrackResponse getParticipantTrack(UUID raceId, UUID participantId) {
        verifyRaceExists(raceId);
        List<GpsLocation> locations = gpsLocationRepository.findByParticipantIdAndRaceIdOrderByRecordedAtAsc(participantId, raceId);

        List<ParticipantTrackPointDto> points = locations.stream()
            .filter(l -> !Boolean.TRUE.equals(l.getFiltered()))
            .map(l -> new ParticipantTrackPointDto(
                GpsDriftFilter.effectiveLatitude(l),
                GpsDriftFilter.effectiveLongitude(l),
                l.getRecordedAt().toString()))
            .collect(Collectors.toList());

        return new ParticipantTrackResponse(points);
    }

    private LiveTrackingResponse buildLiveTrackingResponse(Participant participant, GpsLocation latest, UUID raceId) {
        LiveTrackingResponse row = new LiveTrackingResponse();
        row.setParticipantId(participant.getId());
        row.setBibNumber(participant.getBibNumber());
        row.setParticipantName(participant.getFullName());
        row.setLatitude(GpsDriftFilter.effectiveLatitude(latest));
        row.setLongitude(GpsDriftFilter.effectiveLongitude(latest));
        row.setSpeed(latest.getSpeed());
        row.setStatus(participant.getStatus() != null ? participant.getStatus().name() : null);

        row.setLastCheckpoint(lastCheckpointNameCache.getName(participant.getId(), raceId));

        OffsetDateTime start = participant.getCheckedInAt() != null ? participant.getCheckedInAt() : participant.getRegisteredAt();
        Duration elapsed = null;
        if (start != null && latest.getRecordedAt() != null && latest.getRecordedAt().isAfter(start)) {
            elapsed = Duration.between(start, latest.getRecordedAt());
            row.setElapsedTime(formatDuration(elapsed));
        }

        // Predictive Intelligence
        BigDecimal distanceCovered = participant.getTotalDistance() != null ? participant.getTotalDistance() : BigDecimal.ZERO;
        Race race = participant.getRace();
        if (race != null && race.getDistanceKm() != null) {
            BigDecimal totalRaceDist = race.getDistanceKm();
            BigDecimal remaining = totalRaceDist.subtract(distanceCovered).max(BigDecimal.ZERO);
            row.setDistanceRemaining(remaining);

            if (elapsed != null && distanceCovered.compareTo(BigDecimal.ZERO) > 0) {
                double paceMinutes = (elapsed.toSeconds() / 60.0) / distanceCovered.doubleValue();
                int minutes = (int) paceMinutes;
                int seconds = (int) ((paceMinutes - minutes) * 60);
                row.setAveragePace(String.format("%d:%02d", minutes, seconds));

                if (paceMinutes > 0) {
                    double avgSpeedKmH = 60.0 / paceMinutes;
                    double remainingHours = remaining.doubleValue() / avgSpeedKmH;
                    OffsetDateTime eta = latest.getRecordedAt().plus(Duration.ofMinutes((long) (remainingHours * 60)));
                    row.setPredictedFinishTime(eta.toString());
                }
            }
        }

        // Add splits
        List<CheckpointRecord> records = checkpointRecordRepository.findByParticipantIdAndRaceIdOrderByRecordedAtAsc(participant.getId(), raceId);
        row.setSplits(records.stream().map(r -> {
            Integer distanceMeters = r.getCheckpoint() != null && r.getCheckpoint().getDistanceFromStartKm() != null
                ? r.getCheckpoint().getDistanceFromStartKm().multiply(BigDecimal.valueOf(1000))
                    .setScale(0, java.math.RoundingMode.HALF_UP).intValue()
                : null;
            String timeStr = "---";
            Long durationSeconds = null;
            Long paceSecondsPerKm = null;
            if (start != null && r.getRecordedAt() != null) {
                Duration duration = Duration.between(start, r.getRecordedAt());
                durationSeconds = duration.getSeconds();
                timeStr = formatDuration(duration);
                if (distanceMeters != null && distanceMeters > 0) {
                    paceSecondsPerKm = Math.round(durationSeconds * 1000.0 / distanceMeters);
                }
            }
            return new CheckpointSplitDto(r.getCheckpoint().getName(), timeStr, distanceMeters, durationSeconds, paceSecondsPerKm);
        }).collect(Collectors.toList()));

        return row;
    }

    private Participant resolveCurrentParticipant(UUID raceId) {
        verifyRaceExists(raceId);
        User user = resolveCurrentUser();
        return participantRepository.findByRaceIdAndUserId(raceId, user.getId())
            .orElseThrow(() -> new ResourceNotFoundException("You are not registered for this race"));
    }

    private User resolveCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new UnauthorizedException("Unauthorized");
        }

        return userRepository.findByEmailIgnoreCase(authentication.getName())
            .orElseThrow(() -> new UnauthorizedException("Unauthorized"));
    }

    private void verifyRaceExists(UUID raceId) {
        if (!raceRepository.existsById(raceId)) {
            throw new ResourceNotFoundException("Race not found");
        }
    }

    private CheckpointRecord.RecordMethod parseRecordMethod(String input) {
        if (input == null || input.isBlank()) {
            return CheckpointRecord.RecordMethod.MANUAL;
        }
        String value = input.trim().toUpperCase(Locale.ROOT);
        if ("GPS".equals(value)) {
            return CheckpointRecord.RecordMethod.GPS_AUTO;
        }
        if ("QRCODE".equals(value) || "QR-CODE".equals(value) || "QR_CODE".equals(value)) {
            return CheckpointRecord.RecordMethod.QR_CODE;
        }
        try {
            return CheckpointRecord.RecordMethod.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new ApiException("Invalid record method: " + input);
        }
    }

    private OffsetDateTime parseTimestamp(String timestamp) {
        if (timestamp == null || timestamp.isBlank()) {
            return OffsetDateTime.now();
        }
        try {
            return OffsetDateTime.parse(timestamp);
        } catch (Exception ex) {
            throw new ApiException("Invalid timestamp format");
        }
    }

    private String formatDuration(Duration duration) {
        long totalSeconds = duration.getSeconds();
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    private GpsLocation saveLocation(Participant participant, GpsLocationRequest request) {
        Optional<GpsLocation> prev = gpsLocationRepository.findTopByParticipantIdAndRaceIdOrderByRecordedAtDesc(
            participant.getId(),
            participant.getRace().getId()
        );
        GpsLocation location = buildLocation(participant, request);
        GpsDriftFilter.FilterResult result = gpsDriftFilter.filter(
            prev.orElse(null), request.getLatitude(), request.getLongitude(), request.getAccuracy(), location.getRecordedAt());
        applyFilter(location, result);
        GpsLocation saved = gpsLocationRepository.save(location);

        if (!result.filtered() && result.distanceKm() > 0) {
            participantRepository.incrementTotalDistance(participant.getId(), BigDecimal.valueOf(result.distanceKm()));
        }

        return saved;
    }

    private GpsLocation buildLocation(Participant participant, GpsLocationRequest request) {
        GpsLocation location = new GpsLocation();
        location.setParticipant(participant);
        location.setRace(participant.getRace());
        location.setLatitude(request.getLatitude());
        location.setLongitude(request.getLongitude());
        location.setAltitude(request.getAltitude());
        location.setAccuracy(request.getAccuracy());
        location.setSpeed(request.getSpeed());
        location.setHeading(request.getHeading());
        location.setBatteryLevel(request.getBatteryLevel());
        location.setRecordedAt(parseTimestamp(request.getTimestamp()));
        location.setReceivedAt(OffsetDateTime.now());
        return location;
    }

    private void applyFilter(GpsLocation location, GpsDriftFilter.FilterResult result) {
        location.setFiltered(result.filtered());
        location.setFilterReason(result.reason());
        location.setSmoothedLatitude(result.smoothedLatitude());
        location.setSmoothedLongitude(result.smoothedLongitude());
    }
}
