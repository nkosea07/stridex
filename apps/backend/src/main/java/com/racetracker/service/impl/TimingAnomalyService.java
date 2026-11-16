package com.racetracker.service.impl;

import com.racetracker.config.RaceTrackerProperties;
import com.racetracker.dto.result.TimingAnomalyDto;
import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.model.Checkpoint;
import com.racetracker.model.CheckpointRecord;
import com.racetracker.model.Participant;
import com.racetracker.repository.CheckpointRecordRepository;
import com.racetracker.repository.CheckpointRepository;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.RaceRepository;
import com.racetracker.security.RaceAccessGuard;
import com.racetracker.service.TimingAnomalyDetector;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TimingAnomalyService {

    static final int MAX_ANOMALIES = 500;
    private static final int CHUNK = 1000;

    private final RaceRepository raceRepository;
    private final CheckpointRepository checkpointRepository;
    private final CheckpointRecordRepository checkpointRecordRepository;
    private final ParticipantRepository participantRepository;
    private final RaceAccessGuard raceAccessGuard;
    private final RaceTrackerProperties properties;

    public TimingAnomalyService(RaceRepository raceRepository, CheckpointRepository checkpointRepository,
                                CheckpointRecordRepository checkpointRecordRepository,
                                ParticipantRepository participantRepository, RaceAccessGuard raceAccessGuard,
                                RaceTrackerProperties properties) {
        this.raceRepository = raceRepository;
        this.checkpointRepository = checkpointRepository;
        this.checkpointRecordRepository = checkpointRecordRepository;
        this.participantRepository = participantRepository;
        this.raceAccessGuard = raceAccessGuard;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public List<TimingAnomalyDto> anomalies(UUID raceId) {
        raceAccessGuard.assertCanReadStaff(raceId);
        if (!raceRepository.existsById(raceId)) {
            throw new ResourceNotFoundException("Race not found");
        }
        List<Checkpoint> timedMats = checkpointRepository.findByRaceIdOrderByOrderIndexAsc(raceId).stream()
            .filter(checkpoint -> Boolean.TRUE.equals(checkpoint.getHasRfidReader()))
            .toList();
        List<Participant> participants = participantRepository.findByRaceId(raceId);
        List<TimingAnomalyDto> anomalies = new ArrayList<>();
        double maxSpeed = properties.getTracking().getMaxPlausibleSplitSpeedKmh();
        for (int from = 0; from < participants.size() && anomalies.size() < MAX_ANOMALIES; from += CHUNK) {
            List<Participant> chunk = participants.subList(from, Math.min(from + CHUNK, participants.size()));
            Map<UUID, List<CheckpointRecord>> records = checkpointRecordRepository
                .findByRaceIdAndParticipantIdInOrderByRecordedAtAsc(raceId, chunk.stream().map(Participant::getId).toList())
                .stream().collect(Collectors.groupingBy(record -> record.getParticipant().getId()));
            for (Participant participant : chunk) {
                List<CheckpointRecord> own = records.getOrDefault(participant.getId(), List.of());
                if (!own.isEmpty()) {
                    anomalies.addAll(TimingAnomalyDetector.detect(participant, own, timedMats, maxSpeed));
                }
            }
        }
        return anomalies.size() > MAX_ANOMALIES ? List.copyOf(anomalies.subList(0, MAX_ANOMALIES)) : anomalies;
    }
}
