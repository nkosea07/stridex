package com.racetracker.service.impl;

import com.racetracker.dto.tracking.*;
import com.racetracker.exception.ApiException;
import com.racetracker.exception.ConflictException;
import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.exception.UnauthorizedException;
import com.racetracker.model.*;
import com.racetracker.repository.*;
import com.racetracker.service.TrackingService;
import com.racetracker.websocket.TrackingLivePublisher;
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

    public TrackingServiceImpl(
        RaceRepository raceRepository,
        ParticipantRepository participantRepository,
        UserRepository userRepository,
        GpsLocationRepository gpsLocationRepository,
        CheckpointRepository checkpointRepository,
        CheckpointRecordRepository checkpointRecordRepository,
        TrackingLivePublisher trackingLivePublisher
    ) {
        this.raceRepository = raceRepository;
        this.participantRepository = participantRepository;
        this.userRepository = userRepository;
        this.gpsLocationRepository = gpsLocationRepository;
        this.checkpointRepository = checkpointRepository;
        this.checkpointRecordRepository = checkpointRecordRepository;
        this.trackingLivePublisher = trackingLivePublisher;
    }

    @Override
    @Transactional
    public void recordLocation(UUID raceId, GpsLocationRequest request) {
        Participant participant = resolveCurrentParticipant(raceId);
        GpsLocation latest = saveLocation(participant, request);
        trackingLivePublisher.publishLocationUpdate(raceId, buildLiveTrackingResponse(participant, latest, raceId));
    }

    @Override
    @Transactional
    public void recordLocationsBatch(UUID raceId, GpsLocationBatchRequest request) {
        Participant participant = resolveCurrentParticipant(raceId);
        if (request == null || request.getLocations() == null || request.getLocations().isEmpty()) {
            return;
        }

        GpsLocation latest = null;
        BigDecimal batchDistance = BigDecimal.ZERO;
        
        Optional<GpsLocation> prev = gpsLocationRepository.findTopByParticipantIdAndRaceIdOrderByRecordedAtDesc(participant.getId(), raceId);
        double lastLat = prev.map(l -> l.getLatitude().doubleValue()).orElse(0.0);
        double lastLon = prev.map(l -> l.getLongitude().doubleValue()).orElse(0.0);
        boolean hasPrev = prev.isPresent();

        for (GpsLocationRequest item : request.getLocations()) {
            latest = saveLocationBasic(participant, item);
            
            if (hasPrev) {
                double dist = calculateDistance(lastLat, lastLon, item.getLatitude().doubleValue(), item.getLongitude().doubleValue());
                if (dist > 0.001) {
                    batchDistance = batchDistance.add(BigDecimal.valueOf(dist));
                }
            }
            lastLat = item.getLatitude().doubleValue();
            lastLon = item.getLongitude().doubleValue();
            hasPrev = true;
        }

        if (batchDistance.compareTo(BigDecimal.ZERO) > 0) {
            participantRepository.incrementTotalDistance(participant.getId(), batchDistance);
            participant = participantRepository.findById(participant.getId()).orElse(participant);
        }

        if (latest != null) {
            trackingLivePublisher.publishLocationUpdate(raceId, buildLiveTrackingResponse(participant, latest, raceId));
        }
    }

    @Override
    @Transactional
    public void recordCheckpoint(UUID raceId, TrackingCheckpointRequest request) {
        if (request == null || request.getCheckpointId() == null) {
            throw new ApiException("checkpointId is required");
        }

        Participant participant = resolveCurrentParticipant(raceId);
        Checkpoint checkpoint = checkpointRepository.findByRaceIdAndId(raceId, request.getCheckpointId())
            .orElseThrow(() -> new ResourceNotFoundException("Checkpoint not found"));

        if (checkpointRecordRepository.findByParticipantIdAndCheckpointId(participant.getId(), checkpoint.getId()).isPresent()) {
            throw new ConflictException("Checkpoint already recorded");
        }

        CheckpointRecord record = new CheckpointRecord();
        record.setRace(checkpoint.getRace());
        record.setCheckpoint(checkpoint);
        record.setParticipant(participant);
        record.setRecordedAt(parseTimestamp(request.getTimestamp()));
        record.setRecordMethod(parseRecordMethod(request.getMethod()));
        record.setRecordedBy(resolveCurrentUser());
        record.setIsVerified(true);

        checkpointRecordRepository.save(record);
        
        evictLastCheckpointCache(participant.getId(), raceId);

        if (checkpoint.getCheckpointType() == Checkpoint.CheckpointType.FINISH) {
            participant.setStatus(Participant.ParticipantStatus.FINISHED);
        } else if (participant.getStatus() == Participant.ParticipantStatus.REGISTERED
            || participant.getStatus() == Participant.ParticipantStatus.CONFIRMED
            || participant.getStatus() == Participant.ParticipantStatus.CHECKED_IN) {
            participant.setStatus(Participant.ParticipantStatus.STARTED);
        }
        participantRepository.save(participant);
    }

    @Override
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
    @Transactional(readOnly = true)
    public ParticipantTrackResponse getParticipantTrack(UUID raceId, UUID participantId) {
        verifyRaceExists(raceId);
        List<GpsLocation> locations = gpsLocationRepository.findByParticipantIdAndRaceIdOrderByRecordedAtAsc(participantId, raceId);
        
        List<ParticipantTrackPointDto> points = locations.stream().map(l -> 
            new ParticipantTrackPointDto(l.getLatitude(), l.getLongitude(), l.getRecordedAt().toString())
        ).collect(Collectors.toList());
        
        return new ParticipantTrackResponse(points);
    }

    @org.springframework.cache.annotation.Cacheable(value = "lastCheckpoint", key = "#participantId + '-' + #raceId")
    public String getLastCheckpointName(UUID participantId, UUID raceId) {
        return checkpointRecordRepository.findTopByParticipantIdAndRaceIdOrderByRecordedAtDesc(participantId, raceId)
            .map(record -> record.getCheckpoint() != null ? record.getCheckpoint().getName() : null)
            .orElse(null);
    }

    @org.springframework.cache.annotation.CacheEvict(value = "lastCheckpoint", key = "#participantId + '-' + #raceId")
    public void evictLastCheckpointCache(UUID participantId, UUID raceId) {
        // Method for cache eviction
    }

    private LiveTrackingResponse buildLiveTrackingResponse(Participant participant, GpsLocation latest, UUID raceId) {
        LiveTrackingResponse row = new LiveTrackingResponse();
        row.setParticipantId(participant.getId());
        row.setBibNumber(participant.getBibNumber());
        row.setParticipantName(participant.getFullName());
        row.setLatitude(latest.getLatitude());
        row.setLongitude(latest.getLongitude());
        row.setSpeed(latest.getSpeed());
        row.setStatus(participant.getStatus() != null ? participant.getStatus().name() : null);

        row.setLastCheckpoint(getLastCheckpointName(participant.getId(), raceId));

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
            String timeStr = "---";
            if (start != null && r.getRecordedAt() != null) {
                timeStr = formatDuration(Duration.between(start, r.getRecordedAt()));
            }
            return new CheckpointSplitDto(r.getCheckpoint().getName(), timeStr);
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
        GpsLocation location = saveLocationBasic(participant, request);

        Optional<GpsLocation> prev = gpsLocationRepository.findTopByParticipantIdAndRaceIdOrderByRecordedAtDesc(participant.getId(), participant.getRace().getId());
        if (prev.isPresent()) {
            double dist = calculateDistance(
                prev.get().getLatitude().doubleValue(), prev.get().getLongitude().doubleValue(),
                location.getLatitude().doubleValue(), location.getLongitude().doubleValue()
            );
            if (dist > 0.001) {
                participantRepository.incrementTotalDistance(participant.getId(), BigDecimal.valueOf(dist));
            }
        }

        return location;
    }

    private GpsLocation saveLocationBasic(Participant participant, GpsLocationRequest request) {
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
        return gpsLocationRepository.save(location);
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double earthRadius = 6371; // km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadius * c;
    }
}
