package com.racetracker.service.impl;

import com.racetracker.dto.checkpoint.*;
import com.racetracker.exception.ApiException;
import com.racetracker.exception.ConflictException;
import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.mapper.CheckpointMapper;
import com.racetracker.model.Checkpoint;
import com.racetracker.model.CheckpointRecord;
import com.racetracker.model.Participant;
import com.racetracker.model.Race;
import com.racetracker.model.User;
import com.racetracker.repository.CheckpointRecordRepository;
import com.racetracker.repository.CheckpointRepository;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.RaceRepository;
import com.racetracker.repository.UserRepository;
import com.racetracker.service.CheckpointService;
import com.racetracker.service.CheckpointCutoff;
import com.racetracker.service.LiveSummaryCacheInvalidator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class CheckpointServiceImpl implements CheckpointService {

    private final CheckpointRepository checkpointRepository;
    private final CheckpointRecordRepository checkpointRecordRepository;
    private final ParticipantRepository participantRepository;
    private final RaceRepository raceRepository;
    private final UserRepository userRepository;
    private final CheckpointMapper checkpointMapper;
    private final LiveSummaryCacheInvalidator liveSummaryCacheInvalidator;

    public CheckpointServiceImpl(
        CheckpointRepository checkpointRepository,
        CheckpointRecordRepository checkpointRecordRepository,
        ParticipantRepository participantRepository,
        RaceRepository raceRepository,
        UserRepository userRepository,
        CheckpointMapper checkpointMapper,
        LiveSummaryCacheInvalidator liveSummaryCacheInvalidator
    ) {
        this.checkpointRepository = checkpointRepository;
        this.checkpointRecordRepository = checkpointRecordRepository;
        this.participantRepository = participantRepository;
        this.raceRepository = raceRepository;
        this.userRepository = userRepository;
        this.checkpointMapper = checkpointMapper;
        this.liveSummaryCacheInvalidator = liveSummaryCacheInvalidator;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CheckpointDto> listByRace(UUID raceId) {
        verifyRaceExists(raceId);
        return checkpointRepository.findByRaceIdOrderByOrderIndexAsc(raceId)
            .stream()
            .map(checkpointMapper::toDto)
            .toList();
    }

    @Override
    @Transactional
    public CheckpointDto create(UUID raceId, CheckpointCreateRequest request) {
        Race race = getRace(raceId);

        Checkpoint checkpoint = new Checkpoint();
        checkpoint.setRace(race);
        checkpoint.setName(request.getName().trim());
        checkpoint.setCheckpointType(parseCheckpointType(request.getCheckpointType()));
        checkpoint.setDistanceFromStartKm(request.getDistanceFromStartKm());
        checkpoint.setLatitude(request.getLatitude());
        checkpoint.setLongitude(request.getLongitude());
        checkpoint.setOrderIndex(request.getOrderIndex());
        checkpoint.setCutoffTime(parseDurationNullable(request.getCutoffTime()));
        checkpoint.setHasRfidReader(request.getHasRfidReader() != null ? request.getHasRfidReader() : false);
        checkpoint.setHasQrScanner(request.getHasQrScanner() != null ? request.getHasQrScanner() : false);

        Checkpoint saved = checkpointRepository.save(checkpoint);
        return checkpointMapper.toDto(saved);
    }

    @Override
    @Transactional
    public CheckpointDto update(UUID raceId, UUID checkpointId, CheckpointUpdateRequest request) {
        Checkpoint checkpoint = getCheckpoint(raceId, checkpointId);

        if (request.getName() != null) checkpoint.setName(request.getName());
        if (request.getCheckpointType() != null) checkpoint.setCheckpointType(parseCheckpointType(request.getCheckpointType()));
        if (request.getDistanceFromStartKm() != null) checkpoint.setDistanceFromStartKm(request.getDistanceFromStartKm());
        if (request.getLatitude() != null) checkpoint.setLatitude(request.getLatitude());
        if (request.getLongitude() != null) checkpoint.setLongitude(request.getLongitude());
        if (request.getOrderIndex() != null) checkpoint.setOrderIndex(request.getOrderIndex());
        if (request.getCutoffTime() != null) checkpoint.setCutoffTime(parseDurationNullable(request.getCutoffTime()));
        if (request.getHasRfidReader() != null) checkpoint.setHasRfidReader(request.getHasRfidReader());
        if (request.getHasQrScanner() != null) checkpoint.setHasQrScanner(request.getHasQrScanner());

        liveSummaryCacheInvalidator.evictAllLastCheckpointsAfterCommit();
        Checkpoint saved = checkpointRepository.save(checkpoint);
        return checkpointMapper.toDto(saved);
    }

    @Override
    @Transactional
    public void delete(UUID raceId, UUID checkpointId) {
        Checkpoint checkpoint = getCheckpoint(raceId, checkpointId);
        checkpointRepository.delete(checkpoint);
        liveSummaryCacheInvalidator.evictAllLastCheckpointsAfterCommit();
    }

    @Override
    @Transactional
    public CheckpointRecordDto record(UUID raceId, UUID checkpointId, CheckpointRecordRequest request) {
        Checkpoint checkpoint = getCheckpoint(raceId, checkpointId);
        Participant participant = resolveParticipant(raceId, request);

        if (checkpointRecordRepository.findByParticipantIdAndCheckpointId(participant.getId(), checkpoint.getId()).isPresent()) {
            throw new ConflictException("Checkpoint already recorded for this participant");
        }

        CheckpointRecord record = new CheckpointRecord();
        record.setRace(checkpoint.getRace());
        record.setCheckpoint(checkpoint);
        record.setParticipant(participant);
        OffsetDateTime recordedAt = OffsetDateTime.now();
        record.setRecordedAt(recordedAt);
        record.setRecordMethod(parseRecordMethod(request.getMethod()));
        record.setIsVerified(true);
        record.setRecordedBy(resolveCurrentUserOrNull());

        boolean cutoffMissed = evaluateCutoff(checkpoint, participant, raceId, recordedAt);
        record.setIsCutoffMissed(cutoffMissed);

        CheckpointRecord saved = checkpointRecordRepository.save(record);
        if (cutoffMissed) {
            participant.setStatus(Participant.ParticipantStatus.DNF);
            participantRepository.save(participant);
        }
        liveSummaryCacheInvalidator.evictLastCheckpointAfterCommit(participant.getId(), raceId);
        return checkpointMapper.toRecordDto(saved);
    }

    @Override
    @Transactional
    public CheckpointRecordDto correctRecord(UUID raceId, UUID checkpointId, UUID recordId, CheckpointRecordCorrectionRequest request) {
        getCheckpoint(raceId, checkpointId);
        CheckpointRecord record = checkpointRecordRepository.findByRaceIdAndCheckpointIdAndId(raceId, checkpointId, recordId)
            .orElseThrow(() -> new ResourceNotFoundException("Checkpoint record not found"));

        if (request.getAdjustedRecordedAt() != null) {
            record.setAdjustedRecordedAt(request.getAdjustedRecordedAt());
        }
        if (request.getVerified() != null) {
            record.setIsVerified(request.getVerified());
        }
        if (request.getReason() != null) {
            record.setAdjustmentReason(request.getReason());
        }
        User actor = resolveCurrentUserOrNull();
        record.setAdjustedBy(actor != null ? actor.getId() : null);
        record.setAdjustedAt(OffsetDateTime.now());

        CheckpointRecord saved = checkpointRecordRepository.save(record);
        liveSummaryCacheInvalidator.evictLastCheckpointAfterCommit(saved.getParticipant().getId(), raceId);
        return checkpointMapper.toRecordDto(saved);
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

    private Participant resolveParticipant(UUID raceId, CheckpointRecordRequest request) {
        if (request.getParticipantId() != null) {
            return participantRepository.findByRaceIdAndId(raceId, request.getParticipantId())
                .orElseThrow(() -> new ResourceNotFoundException("Participant not found"));
        }

        if (request.getParticipantBibNumber() != null && !request.getParticipantBibNumber().isBlank()) {
            return participantRepository.findByRaceIdAndBibNumber(raceId, request.getParticipantBibNumber().trim())
                .orElseThrow(() -> new ResourceNotFoundException("Participant not found"));
        }

        throw new ApiException("participantId or participantBibNumber is required");
    }

    private CheckpointRecord.RecordMethod parseRecordMethod(String input) {
        if (input == null || input.isBlank()) {
            return CheckpointRecord.RecordMethod.MANUAL;
        }
        try {
            return CheckpointRecord.RecordMethod.valueOf(input.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ApiException("Invalid record method: " + input);
        }
    }

    private Checkpoint.CheckpointType parseCheckpointType(String input) {
        if (input == null || input.isBlank()) {
            throw new ApiException("Checkpoint type is required");
        }
        try {
            return Checkpoint.CheckpointType.valueOf(input.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ApiException("Invalid checkpoint type: " + input);
        }
    }

    private Duration parseDurationNullable(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }

        String[] parts = input.split(":");
        try {
            if (parts.length != 3) {
                throw new ApiException("Duration must be HH:mm:ss");
            }
            long hours = Long.parseLong(parts[0]);
            long minutes = Long.parseLong(parts[1]);
            long seconds = Long.parseLong(parts[2]);
            return Duration.ofHours(hours).plusMinutes(minutes).plusSeconds(seconds);
        } catch (NumberFormatException ex) {
            throw new ApiException("Duration must be HH:mm:ss");
        }
    }

    private Checkpoint getCheckpoint(UUID raceId, UUID checkpointId) {
        return checkpointRepository.findByRaceIdAndId(raceId, checkpointId)
            .orElseThrow(() -> new ResourceNotFoundException("Checkpoint not found"));
    }

    private Race getRace(UUID raceId) {
        return raceRepository.findById(raceId)
            .orElseThrow(() -> new ResourceNotFoundException("Race not found"));
    }

    private void verifyRaceExists(UUID raceId) {
        if (!raceRepository.existsById(raceId)) {
            throw new ResourceNotFoundException("Race not found");
        }
    }

    private User resolveCurrentUserOrNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        return userRepository.findByEmailIgnoreCase(authentication.getName()).orElse(null);
    }
}
