package com.racetracker.service.impl;

import com.racetracker.dto.rfid.GunTimeDto;
import com.racetracker.dto.rfid.GunTimeRequest;
import com.racetracker.dto.rfid.RfidReprocessRequest;
import com.racetracker.dto.rfid.RfidReprocessResultDto;
import com.racetracker.dto.rfid.RfidUnmatchedTagDto;
import com.racetracker.exception.ApiException;
import com.racetracker.exception.ConflictException;
import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.model.Race;
import com.racetracker.model.RfidReadEvent;
import com.racetracker.repository.RaceRepository;
import com.racetracker.repository.RfidReadEventRepository;
import com.racetracker.security.RaceAccessGuard;
import com.racetracker.service.AuditEventService;
import com.racetracker.service.CheckpointCutoff;
import com.racetracker.service.RfidTimingService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RfidTimingServiceImpl implements RfidTimingService {

    private static final int MAX_UNMATCHED_TAGS = 200;
    private static final Duration MAX_GUN_TIME_AHEAD = Duration.ofMinutes(5);

    private final RaceRepository raceRepository;
    private final RfidReadEventRepository rfidReadEventRepository;
    private final RaceAccessGuard raceAccessGuard;
    private final AuditEventService auditEventService;
    private final com.racetracker.service.ResultLock resultLock;
    private final com.racetracker.service.TimingRegate timingRegate;

    public RfidTimingServiceImpl(RaceRepository raceRepository, RfidReadEventRepository rfidReadEventRepository,
                                 RaceAccessGuard raceAccessGuard, AuditEventService auditEventService,
                                 com.racetracker.service.ResultLock resultLock,
                                 com.racetracker.service.TimingRegate timingRegate) {
        this.raceRepository = raceRepository;
        this.rfidReadEventRepository = rfidReadEventRepository;
        this.raceAccessGuard = raceAccessGuard;
        this.auditEventService = auditEventService;
        this.resultLock = resultLock;
        this.timingRegate = timingRegate;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RfidUnmatchedTagDto> unmatchedTags(UUID raceId) {
        raceAccessGuard.assertCanReadStaff(raceId);
        return rfidReadEventRepository.findUnmatchedTagSummary(raceId, PageRequest.of(0, MAX_UNMATCHED_TAGS)).stream()
            .map(row -> new RfidUnmatchedTagDto(
                (String) row[0],
                ((Number) row[1]).longValue(),
                row[2] == null ? null : row[2].toString(),
                row[3] == null ? null : row[3].toString()))
            .toList();
    }

    @Override
    @Transactional
    public RfidReprocessResultDto reprocess(UUID raceId, RfidReprocessRequest request, String actorEmail) {
        raceAccessGuard.assertCanManage(raceId);
        resultLock.assertOpen(raceId);
        Set<RfidReadEvent.RfidReadEventStatus> statuses = request.getStatuses().stream()
            .map(scope -> RfidReadEvent.RfidReadEventStatus.valueOf(scope.name()))
            .collect(Collectors.toCollection(() -> EnumSet.noneOf(RfidReadEvent.RfidReadEventStatus.class)));
        int requeued = rfidReadEventRepository.requeueByRaceIdAndStatusIn(raceId, statuses);
        auditEventService.recordForActor("RFID_READS_REPROCESSED", actorEmail, "RACE", raceId.toString(), "SUCCESS",
            Map.of("statuses", statuses.toString(), "requeued", String.valueOf(requeued)));
        return new RfidReprocessResultDto(requeued);
    }

    @Override
    @Transactional(readOnly = true)
    public GunTimeDto gunTime(UUID raceId) {
        raceAccessGuard.assertCanReadStaff(raceId);
        return toDto(requireRace(raceId));
    }

    @Override
    @Transactional
    public GunTimeDto correctGunTime(UUID raceId, GunTimeRequest request, String actorEmail) {
        raceAccessGuard.assertCanManage(raceId);
        Race race = requireRace(raceId);
        resultLock.assertOpen(race);
        if (race.getStatus() == Race.RaceStatus.DRAFT || race.getStatus() == Race.RaceStatus.CANCELLED) {
            throw new ConflictException("The gun time can only be set for a race that is running or finished",
                "GUN_TIME_NOT_ALLOWED");
        }
        if (request.getStartedAt().isAfter(OffsetDateTime.now().plus(MAX_GUN_TIME_AHEAD))) {
            throw new ApiException("The gun time cannot be in the future", "GUN_TIME_IN_FUTURE");
        }
        OffsetDateTime previous = race.getStartedAt();
        race.setStartedAt(request.getStartedAt());
        raceRepository.save(race);
        com.racetracker.service.TimingRegate.Outcome outcome = timingRegate.apply(raceId, null, request.getStartedAt());
        auditEventService.recordForActor("RACE_GUN_TIME_CORRECTED", actorEmail, "RACE", raceId.toString(), "SUCCESS",
            Map.of("previous", String.valueOf(previous), "startedAt", request.getStartedAt().toString(),
                "reason", request.getReason().trim(), "timesMoved", String.valueOf(outcome.moved()),
                "timesRemoved", String.valueOf(outcome.removed())));
        return toDto(race);
    }

    private Race requireRace(UUID raceId) {
        return raceRepository.findById(raceId).orElseThrow(() -> new ResourceNotFoundException("Race not found"));
    }

    private GunTimeDto toDto(Race race) {
        OffsetDateTime scheduled = CheckpointCutoff.scheduledStart(race);
        return new GunTimeDto(
            race.getStartedAt() == null ? null : race.getStartedAt().toString(),
            scheduled == null ? null : scheduled.toString());
    }
}
