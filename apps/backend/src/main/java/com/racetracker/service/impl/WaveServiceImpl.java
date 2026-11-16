package com.racetracker.service.impl;

import com.racetracker.dto.race.RaceWaveDto;
import com.racetracker.dto.race.RaceWaveRequest;
import com.racetracker.dto.race.WaveAssignmentRequest;
import com.racetracker.dto.race.WaveStartRequest;
import com.racetracker.exception.ApiException;
import com.racetracker.exception.ConflictException;
import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.model.Participant;
import com.racetracker.model.Race;
import com.racetracker.model.RaceWave;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.RaceRepository;
import com.racetracker.repository.RaceWaveRepository;
import com.racetracker.repository.UserRepository;
import com.racetracker.security.RaceAccessGuard;
import com.racetracker.service.AuditEventService;
import com.racetracker.service.ResultLock;
import com.racetracker.service.TimingRegate;
import com.racetracker.service.WaveService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WaveServiceImpl implements WaveService {

    private static final Duration MAX_GUN_AHEAD = Duration.ofMinutes(5);

    private final RaceWaveRepository waveRepository;
    private final RaceRepository raceRepository;
    private final ParticipantRepository participantRepository;
    private final UserRepository userRepository;
    private final RaceAccessGuard raceAccessGuard;
    private final ResultLock resultLock;
    private final TimingRegate timingRegate;
    private final AuditEventService auditEventService;

    public WaveServiceImpl(RaceWaveRepository waveRepository, RaceRepository raceRepository,
                           ParticipantRepository participantRepository, UserRepository userRepository,
                           RaceAccessGuard raceAccessGuard, ResultLock resultLock, TimingRegate timingRegate,
                           AuditEventService auditEventService) {
        this.waveRepository = waveRepository;
        this.raceRepository = raceRepository;
        this.participantRepository = participantRepository;
        this.userRepository = userRepository;
        this.raceAccessGuard = raceAccessGuard;
        this.resultLock = resultLock;
        this.timingRegate = timingRegate;
        this.auditEventService = auditEventService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RaceWaveDto> list(UUID raceId) {
        raceAccessGuard.assertCanReadStaff(raceId);
        return waveRepository.findByRaceIdOrderBySortOrderAscNameAsc(raceId).stream().map(this::toDto).toList();
    }

    @Override
    @Transactional
    public RaceWaveDto create(UUID raceId, RaceWaveRequest request) {
        raceAccessGuard.assertCanManage(raceId);
        Race race = raceRepository.findById(raceId).orElseThrow(() -> new ResourceNotFoundException("Race not found"));
        resultLock.assertOpen(race);
        String name = request.getName().trim();
        if (waveRepository.existsByRaceIdAndNameIgnoreCase(raceId, name)) {
            throw new ConflictException("A wave with this name already exists", "WAVE_NAME_IN_USE");
        }
        RaceWave wave = RaceWave.builder().race(race).name(name)
            .sortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder())
            .scheduledStart(request.getScheduledStart()).build();
        RaceWave saved = waveRepository.save(wave);
        audit("RACE_WAVE_CREATED", saved, Map.of("name", name));
        return toDto(saved);
    }

    @Override
    @Transactional
    public RaceWaveDto update(UUID raceId, UUID waveId, RaceWaveRequest request) {
        raceAccessGuard.assertCanManage(raceId);
        RaceWave wave = requireWave(raceId, waveId);
        resultLock.assertOpen(wave.getRace());
        String name = request.getName().trim();
        if (!name.equalsIgnoreCase(wave.getName()) && waveRepository.existsByRaceIdAndNameIgnoreCase(raceId, name)) {
            throw new ConflictException("A wave with this name already exists", "WAVE_NAME_IN_USE");
        }
        boolean scheduleChanged = !java.util.Objects.equals(wave.getScheduledStart(), request.getScheduledStart());
        wave.setName(name);
        if (request.getSortOrder() != null) {
            wave.setSortOrder(request.getSortOrder());
        }
        wave.setScheduledStart(request.getScheduledStart());
        RaceWave saved = waveRepository.save(wave);
        audit("RACE_WAVE_UPDATED", saved, Map.of("name", name,
            "scheduledStart", String.valueOf(request.getScheduledStart())));
        if (scheduleChanged && saved.getStartedAt() == null && saved.getScheduledStart() != null) {
            timingRegate.apply(raceId, waveId, saved.getScheduledStart());
        }
        return toDto(saved);
    }

    @Override
    @Transactional
    public void delete(UUID raceId, UUID waveId) {
        raceAccessGuard.assertCanManage(raceId);
        RaceWave wave = requireWave(raceId, waveId);
        resultLock.assertOpen(wave.getRace());
        if (participantRepository.countByWaveId(waveId) > 0) {
            throw new ConflictException("Move this wave's participants to another wave before deleting it", "WAVE_HAS_PARTICIPANTS");
        }
        audit("RACE_WAVE_DELETED", wave, Map.of("name", wave.getName()));
        waveRepository.delete(wave);
    }

    @Override
    @Transactional
    public RaceWaveDto start(UUID raceId, UUID waveId, WaveStartRequest request) {
        boolean manager = raceAccessGuard.canManage(raceId);
        if (!manager) {
            raceAccessGuard.assertCanPerformOperationalAction(raceId);
        }
        RaceWave wave = requireWave(raceId, waveId);
        resultLock.assertOpen(wave.getRace());
        if (!manager && wave.getStartedAt() != null) {
            throw new org.springframework.security.access.AccessDeniedException("Only race admins can correct a wave gun time");
        }
        if (!manager && request != null && request.getStartedAt() != null) {
            throw new ApiException("Officials start a wave at the current time; ask a race admin to set an earlier time",
                "GUN_TIME_OFFICIAL_NOW_ONLY");
        }
        OffsetDateTime gun = request == null || request.getStartedAt() == null ? OffsetDateTime.now() : request.getStartedAt();
        if (gun.isAfter(OffsetDateTime.now().plus(MAX_GUN_AHEAD))) {
            throw new ApiException("The gun time cannot be in the future", "GUN_TIME_IN_FUTURE");
        }
        String reason = request == null || request.getReason() == null ? "" : request.getReason().trim();
        boolean correction = wave.getStartedAt() != null;
        if (correction && reason.length() < 5) {
            throw new ApiException("A reason is required to correct a wave gun time", "GUN_TIME_REASON_REQUIRED");
        }
        OffsetDateTime previous = wave.getStartedAt();
        wave.setStartedAt(gun);
        RaceWave saved = waveRepository.save(wave);
        TimingRegate.Outcome outcome = timingRegate.apply(raceId, waveId, gun);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("previous", String.valueOf(previous));
        details.put("startedAt", gun.toString());
        details.put("reason", reason);
        details.put("timesMoved", outcome.moved());
        details.put("timesRemoved", outcome.removed());
        audit(correction ? "RACE_WAVE_GUN_CORRECTED" : "RACE_WAVE_STARTED", saved, details);
        return toDto(saved);
    }

    @Override
    @Transactional
    public int assign(UUID raceId, UUID waveId, WaveAssignmentRequest request) {
        raceAccessGuard.assertCanManage(raceId);
        RaceWave wave = requireWave(raceId, waveId);
        resultLock.assertOpen(wave.getRace());
        List<Participant> participants = participantRepository.findByRaceIdAndIdIn(raceId, request.getParticipantIds());
        if (participants.size() != new java.util.HashSet<>(request.getParticipantIds()).size()) {
            throw new ResourceNotFoundException("One or more participants were not found in this race");
        }
        for (Participant participant : participants) {
            participant.setWave(wave);
        }
        participantRepository.saveAll(participants);
        audit("RACE_WAVE_ASSIGNED", wave, Map.of("participants", participants.size()));
        return participants.size();
    }

    private RaceWave requireWave(UUID raceId, UUID waveId) {
        return waveRepository.findByRaceIdAndId(raceId, waveId)
            .orElseThrow(() -> new ResourceNotFoundException("Wave not found"));
    }

    private RaceWaveDto toDto(RaceWave wave) {
        return new RaceWaveDto(wave.getId(), wave.getName(), wave.getSortOrder() == null ? 0 : wave.getSortOrder(),
            wave.getScheduledStart() == null ? null : wave.getScheduledStart().toString(),
            wave.getStartedAt() == null ? null : wave.getStartedAt().toString(),
            wave.getId() == null ? 0 : participantRepository.countByWaveId(wave.getId()));
    }

    private void audit(String action, RaceWave wave, Map<String, Object> details) {
        Map<String, Object> metadata = new LinkedHashMap<>(details);
        metadata.put("raceId", wave.getRace().getId().toString());
        auditEventService.recordInCurrentTransaction(action, actorId(), "RACE_WAVE", String.valueOf(wave.getId()), "SUCCESS", metadata);
    }

    private UUID actorId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        return userRepository.findByEmailIgnoreCase(authentication.getName()).map(com.racetracker.model.User::getId).orElse(null);
    }
}
