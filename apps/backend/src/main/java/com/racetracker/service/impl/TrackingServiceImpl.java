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
import com.racetracker.service.LiveSummaryCacheInvalidator;
import com.racetracker.service.CheckpointProgression;
import com.racetracker.service.SelfScanTokens;
import com.racetracker.service.TrackingService;
import com.racetracker.security.CapabilityAuthorizationService;
import com.racetracker.websocket.TrackingLivePublisher;
import io.micrometer.observation.annotation.Observed;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TrackingServiceImpl implements TrackingService {

    static final String TRACKING_CLOSED = "TRACKING_CLOSED";
    private static final int LIVE_QUERY_CHUNK = 1000;
    private static final Set<Participant.ParticipantStatus> NON_ENTRANTS = EnumSet.of(
        Participant.ParticipantStatus.PENDING_REVIEW,
        Participant.ParticipantStatus.WAITLISTED,
        Participant.ParticipantStatus.REJECTED,
        Participant.ParticipantStatus.WITHDRAWN
    );
    private static final Set<Participant.ParticipantStatus> TRACKING_FINISHED = EnumSet.of(
        Participant.ParticipantStatus.FINISHED,
        Participant.ParticipantStatus.DNF,
        Participant.ParticipantStatus.DNS,
        Participant.ParticipantStatus.DISQUALIFIED
    );
    private static final Set<Race.RaceStatus> RACE_CLOSED = EnumSet.of(
        Race.RaceStatus.COMPLETED,
        Race.RaceStatus.CANCELLED
    );

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
    private final CheckpointProgression checkpointProgression;
    private final CapabilityAuthorizationService capabilityAuthorizationService;
    private final SelfScanClaimRepository selfScanClaimRepository;
    private final com.racetracker.config.RaceTrackerProperties properties;

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
        CheckpointProgression checkpointProgression,
        CapabilityAuthorizationService capabilityAuthorizationService,
        SelfScanClaimRepository selfScanClaimRepository,
        com.racetracker.config.RaceTrackerProperties properties
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
        this.checkpointProgression = checkpointProgression;
        this.capabilityAuthorizationService = capabilityAuthorizationService;
        this.selfScanClaimRepository = selfScanClaimRepository;
        this.properties = properties;
    }

    @Override
    @Observed(name = "tracking.ingest.location")
    @Transactional
    public void recordLocation(UUID raceId, GpsLocationRequest request) {
        capabilityAuthorizationService.require(com.racetracker.model.UserCapabilityType.ATHLETE);
        ingest(raceId, List.of(request));
    }

    @Override
    @Observed(name = "tracking.ingest.location_batch")
    @Transactional
    public void recordLocationsBatch(UUID raceId, GpsLocationBatchRequest request) {
        capabilityAuthorizationService.require(com.racetracker.model.UserCapabilityType.ATHLETE);
        if (request == null || request.getLocations() == null || request.getLocations().isEmpty()) {
            resolveTrackableParticipant(raceId);
            return;
        }
        ingest(raceId, request.getLocations());
    }

    private void ingest(UUID raceId, List<GpsLocationRequest> requests) {
        Participant participant = resolveTrackableParticipant(raceId);
        gpsLocationRepository.lockLocationStream("gps-stream:" + participant.getId());
        boolean distanceCounts = participant.getRace() != null
            && participant.getRace().getStatus() == Race.RaceStatus.IN_PROGRESS;

        List<GpsLocation> ordered = requests.stream()
            .map(item -> buildLocation(participant, item))
            .sorted(Comparator.comparing(GpsLocation::getRecordedAt))
            .toList();

        GpsLocation lastAccepted = gpsLocationRepository
            .findTopByParticipantIdAndRaceIdAndFilteredFalseAndRecordedAtLessThanEqualOrderByRecordedAtDesc(
                participant.getId(), raceId, gpsDriftFilter.latestAcceptableRecordedAt())
            .orElse(null);
        GpsLocation newestAccepted = null;
        BigDecimal batchDistance = BigDecimal.ZERO;

        for (GpsLocation location : ordered) {
            GpsDriftFilter.FilterResult result = gpsDriftFilter.filter(
                lastAccepted, location.getLatitude(), location.getLongitude(), location.getAccuracy(),
                location.getRecordedAt());
            applyFilter(location, result);
            GpsLocation saved = gpsLocationRepository.save(location);
            if (!result.filtered()) {
                if (distanceCounts && result.distanceKm() > 0) {
                    batchDistance = batchDistance.add(BigDecimal.valueOf(result.distanceKm()));
                }
                lastAccepted = saved;
                newestAccepted = saved;
            }
        }

        Participant current = participant;
        if (batchDistance.compareTo(BigDecimal.ZERO) > 0) {
            participantRepository.incrementTotalDistance(participant.getId(), batchDistance);
            current = participantRepository.findById(participant.getId()).orElse(participant);
        }

        if (newestAccepted != null) {
            List<CheckpointRecord> records = checkpointRecordRepository
                .findByParticipantIdAndRaceIdOrderByRecordedAtAsc(current.getId(), raceId);
            LiveTrackingResponse update = buildLiveTrackingResponse(current, newestAccepted, records);
            publishAfterCommit(raceId, update);
        }
    }

    @Override
    @Transactional
    public void publishTimingUpdate(UUID raceId, UUID participantId) {
        Participant participant = participantRepository.findById(participantId).orElse(null);
        if (participant == null) {
            return;
        }
        GpsLocation latest = gpsLocationRepository
            .findTopByParticipantIdAndRaceIdAndFilteredFalseAndRecordedAtLessThanEqualOrderByRecordedAtDesc(
                participantId, raceId, gpsDriftFilter.latestAcceptableRecordedAt())
            .orElse(null);
        List<CheckpointRecord> records = checkpointRecordRepository
            .findByParticipantIdAndRaceIdOrderByRecordedAtAsc(participantId, raceId);
        LiveTrackingResponse update = buildLiveTrackingResponse(participant, latest, records);
        if (update != null) {
            publishAfterCommit(raceId, update);
        }
    }

    private Participant resolveTrackableParticipant(UUID raceId) {
        Participant participant = resolveCurrentParticipant(raceId);
        Participant.ParticipantStatus status = participant.getStatus();
        if (status != null && NON_ENTRANTS.contains(status)) {
            throw new AccessDeniedException("Your registration has not been accepted for this race");
        }
        Race race = participant.getRace();
        if ((race != null && race.getStatus() != null && RACE_CLOSED.contains(race.getStatus()))
            || (status != null && TRACKING_FINISHED.contains(status))) {
            throw new ConflictException("Live tracking has ended for this race entry", TRACKING_CLOSED);
        }
        return participant;
    }

    private void publishAfterCommit(UUID raceId, LiveTrackingResponse update) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    trackingLivePublisher.publishLocationUpdate(raceId, update);
                }
            });
        } else {
            trackingLivePublisher.publishLocationUpdate(raceId, update);
        }
    }

    @Override
    @Observed(name = "tracking.ingest.checkpoint")
    @Transactional
    public SelfScanResultDto recordCheckpoint(UUID raceId, TrackingCheckpointRequest request) {
        capabilityAuthorizationService.require(com.racetracker.model.UserCapabilityType.ATHLETE);
        if (request == null || request.getCheckpointId() == null) {
            throw new ApiException("checkpointId is required", "CHECKPOINT_REQUIRED");
        }

        Participant participant = resolveTrackableParticipant(raceId);
        Race race = participant.getRace();
        if (race == null || race.getStatus() != Race.RaceStatus.IN_PROGRESS) {
            throw new ConflictException("Checkpoints can only be scanned while the race is in progress",
                "RACE_NOT_IN_PROGRESS");
        }
        Checkpoint checkpoint = checkpointRepository.findByRaceIdAndId(raceId, request.getCheckpointId())
            .orElseThrow(() -> new ResourceNotFoundException("Checkpoint not found"));
        if (!checkpoint.acceptsSelfScan()) {
            throw new ConflictException("Start and finish times come from official timing", "SELF_SCAN_NOT_ALLOWED");
        }
        if (!SelfScanTokens.matches(checkpoint.getSelfScanToken(), request.getScanToken())) {
            throw new ApiException("This checkpoint code is not valid. Scan the code displayed at the checkpoint.",
                "SELF_SCAN_CODE_INVALID");
        }
        if (checkpointRecordRepository.findFirstByParticipantIdAndCheckpointIdOrderByPassNumberAsc(participant.getId(), checkpoint.getId()).isPresent()) {
            throw new ConflictException("Checkpoint already recorded", "CHECKPOINT_ALREADY_RECORDED");
        }

        if (request.getLocation() != null) {
            ingest(raceId, List.of(request.getLocation()));
        } else {
            gpsLocationRepository.lockLocationStream("gps-stream:" + participant.getId());
        }
        OffsetDateTime scannedAt = OffsetDateTime.now();
        if (selfScanClaimRepository.existsByParticipantIdAndCheckpointIdAndStatus(
            participant.getId(), checkpoint.getId(), SelfScanClaim.Status.PENDING)) {
            return SelfScanResultDto.provisional("ALREADY_PENDING");
        }

        ProximityCheck proximity = checkProximity(participant, checkpoint, raceId, scannedAt);
        if (proximity.reason() != null) {
            SelfScanClaim claim = new SelfScanClaim();
            claim.setRace(race);
            claim.setCheckpoint(checkpoint);
            claim.setParticipant(participant);
            claim.setClaimedAt(scannedAt);
            claim.setStatus(SelfScanClaim.Status.PENDING);
            claim.setReason(proximity.reason());
            claim.setDistanceMeters(proximity.distanceMeters());
            claim.setFixAgeSeconds(proximity.fixAgeSeconds());
            selfScanClaimRepository.save(claim);
            return SelfScanResultDto.provisional(proximity.reason().name());
        }

        boolean cutoffMissed = evaluateCutoff(checkpoint, participant, raceId, scannedAt);
        CheckpointRecord record = new CheckpointRecord();
        record.setRace(checkpoint.getRace());
        record.setCheckpoint(checkpoint);
        record.setParticipant(participant);
        record.setRecordedAt(scannedAt);
        record.setRecordMethod(CheckpointRecord.RecordMethod.QR_CODE);
        record.setRecordedBy(resolveCurrentUser());
        record.setIsVerified(true);
        record.setIsCutoffMissed(cutoffMissed);
        record.setNotes("Self-scan confirmed by GPS within " + proximity.distanceMeters() + " m");
        checkpointRecordRepository.save(record);

        liveSummaryCacheInvalidator.evictLastCheckpointAfterCommit(participant.getId(), raceId);
        checkpointProgression.apply(participant, checkpoint, cutoffMissed);
        participantRepository.save(participant);
        return SelfScanResultDto.verified();
    }

    private record ProximityCheck(SelfScanClaim.Reason reason, BigDecimal distanceMeters, Integer fixAgeSeconds) {
    }

    private ProximityCheck checkProximity(Participant participant, Checkpoint checkpoint, UUID raceId,
                                          OffsetDateTime scannedAt) {
        if (checkpoint.getLatitude() == null || checkpoint.getLongitude() == null) {
            return new ProximityCheck(SelfScanClaim.Reason.CHECKPOINT_NOT_LOCATED, null, null);
        }
        GpsLocation fix = gpsLocationRepository
            .findTopByParticipantIdAndRaceIdAndFilteredFalseAndRecordedAtLessThanEqualOrderByRecordedAtDesc(
                participant.getId(), raceId, gpsDriftFilter.latestAcceptableRecordedAt())
            .orElse(null);
        if (fix == null || fix.getRecordedAt() == null) {
            return new ProximityCheck(SelfScanClaim.Reason.NO_RECENT_FIX, null, null);
        }
        long ageSeconds = Math.abs(Duration.between(fix.getRecordedAt(), scannedAt).getSeconds());
        int fixAge = (int) Math.min(ageSeconds, Integer.MAX_VALUE);
        double meters = geoTelemetryService.calculateDistanceKm(
            GpsDriftFilter.effectiveLatitude(fix), GpsDriftFilter.effectiveLongitude(fix),
            checkpoint.getLatitude(), checkpoint.getLongitude()) * 1000.0;
        BigDecimal distance = BigDecimal.valueOf(meters).setScale(1, java.math.RoundingMode.HALF_UP);
        if (ageSeconds > Math.max(properties.getTracking().getSelfScanMaxFixAgeSeconds(), 1)) {
            return new ProximityCheck(SelfScanClaim.Reason.NO_RECENT_FIX, distance, fixAge);
        }
        double accuracyAllowance = fix.getAccuracy() == null ? 0
            : Math.min(fix.getAccuracy().doubleValue(),
                Math.max(properties.getTracking().getSelfScanAccuracyAllowanceMeters(), 0));
        double allowed = Math.max(properties.getTracking().getCheckpointRadiusMeters(), 1) + accuracyAllowance;
        if (meters > allowed) {
            return new ProximityCheck(SelfScanClaim.Reason.OUT_OF_RANGE, distance, fixAge);
        }
        return new ProximityCheck(null, distance, fixAge);
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

        for (int from = 0; from < participants.size(); from += LIVE_QUERY_CHUNK) {
            List<Participant> chunk = participants.subList(from, Math.min(from + LIVE_QUERY_CHUNK, participants.size()));
            List<UUID> ids = chunk.stream().map(Participant::getId).toList();
            Map<UUID, GpsLocation> latestById = new HashMap<>();
            for (GpsLocation location : gpsLocationRepository.findLatestByRaceIdAndParticipantIds(raceId, ids)) {
                latestById.putIfAbsent(location.getParticipant().getId(), location);
            }
            Map<UUID, List<CheckpointRecord>> recordsById = checkpointRecordRepository
                .findByRaceIdAndParticipantIdInOrderByRecordedAtAsc(raceId, ids)
                .stream()
                .collect(Collectors.groupingBy(record -> record.getParticipant().getId()));
            for (Participant participant : chunk) {
                LiveTrackingResponse row = buildLiveTrackingResponse(participant, latestById.get(participant.getId()),
                    recordsById.getOrDefault(participant.getId(), List.of()));
                if (row != null) {
                    results.add(row);
                }
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

    /**
     * Position comes from the most recent of the athlete's GPS fix and their last timed checkpoint
     * that has coordinates, so athletes timed only by RFID mats still appear on the staff map.
     */
    private LiveTrackingResponse buildLiveTrackingResponse(Participant participant, GpsLocation latest,
                                                           List<CheckpointRecord> records) {
        CheckpointRecord positioned = null;
        for (CheckpointRecord record : records) {
            Checkpoint checkpoint = record.getCheckpoint();
            OffsetDateTime effective = CheckpointCutoff.effectiveTime(record);
            if (checkpoint != null && checkpoint.getLatitude() != null && checkpoint.getLongitude() != null
                && effective != null
                && (positioned == null || effective.isAfter(CheckpointCutoff.effectiveTime(positioned)))) {
                positioned = record;
            }
        }
        boolean useGps = latest != null && (positioned == null || latest.getRecordedAt() == null
            || !latest.getRecordedAt().isBefore(CheckpointCutoff.effectiveTime(positioned)));
        if (!useGps && positioned == null) {
            return null;
        }

        LiveTrackingResponse row = new LiveTrackingResponse();
        row.setParticipantId(participant.getId());
        row.setBibNumber(participant.getBibNumber());
        row.setParticipantName(participant.getFullName());
        if (useGps) {
            row.setLatitude(GpsDriftFilter.effectiveLatitude(latest));
            row.setLongitude(GpsDriftFilter.effectiveLongitude(latest));
            row.setSpeed(latest.getSpeed());
            row.setRecordedAt(latest.getRecordedAt() != null ? latest.getRecordedAt().toString() : null);
            row.setPositionSource("GPS");
        } else {
            row.setLatitude(positioned.getCheckpoint().getLatitude());
            row.setLongitude(positioned.getCheckpoint().getLongitude());
            row.setRecordedAt(CheckpointCutoff.effectiveTime(positioned).toString());
            row.setPositionSource("CHECKPOINT");
        }
        row.setStatus(participant.getStatus() != null ? participant.getStatus().name() : null);

        CheckpointRecord lastRecord = records.isEmpty() ? null : records.get(records.size() - 1);
        row.setLastCheckpoint(lastRecord != null && lastRecord.getCheckpoint() != null
            ? lastRecord.getCheckpoint().getName() : null);

        OffsetDateTime start = CheckpointCutoff.resolveStart(participant, records);
        com.racetracker.service.RaceProgress.Snapshot progress =
            com.racetracker.service.RaceProgress.compute(participant, latest, records);
        if (progress.elapsed() != null) {
            row.setElapsedTime(formatDuration(progress.elapsed()));
        }
        row.setDistanceRemaining(progress.distanceRemainingKm());
        row.setAveragePace(progress.averagePace());
        row.setPredictedFinishTime(progress.predictedFinish() != null ? progress.predictedFinish().toString() : null);

        row.setSplits(records.stream().map(r -> {
            Integer distanceMeters = r.getCheckpoint() != null && r.getCheckpoint().getDistanceFromStartKm() != null
                ? r.getCheckpoint().getDistanceFromStartKm().multiply(BigDecimal.valueOf(1000))
                    .setScale(0, java.math.RoundingMode.HALF_UP).intValue()
                : null;
            String timeStr = "---";
            Long durationSeconds = null;
            Long paceSecondsPerKm = null;
            OffsetDateTime effective = CheckpointCutoff.effectiveTime(r);
            if (start != null && effective != null && !effective.isBefore(start)) {
                Duration duration = Duration.between(start, effective);
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
