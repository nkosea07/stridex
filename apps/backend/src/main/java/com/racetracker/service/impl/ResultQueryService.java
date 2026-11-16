package com.racetracker.service.impl;

import com.racetracker.dto.result.ResultQueryDto;
import com.racetracker.dto.result.ResultQueryRequest;
import com.racetracker.dto.result.ResultQueryResolution;
import com.racetracker.exception.ConflictException;
import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.exception.UnauthorizedException;
import com.racetracker.model.Checkpoint;
import com.racetracker.model.Notification;
import com.racetracker.model.Participant;
import com.racetracker.model.ResultQuery;
import com.racetracker.model.User;
import com.racetracker.repository.CheckpointRepository;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.ResultQueryRepository;
import com.racetracker.repository.UserRepository;
import com.racetracker.security.RaceAccessGuard;
import com.racetracker.service.AuditEventService;
import com.racetracker.service.NotificationService;
import com.racetracker.service.ResultLock;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ResultQueryService {

    private static final int MAX_OPEN_PER_ENTRY = 3;
    private static final int MAX_LISTED = 200;

    private final ResultQueryRepository queryRepository;
    private final ParticipantRepository participantRepository;
    private final CheckpointRepository checkpointRepository;
    private final UserRepository userRepository;
    private final RaceAccessGuard raceAccessGuard;
    private final ResultLock resultLock;
    private final AuditEventService auditEventService;
    private final NotificationService notificationService;

    public ResultQueryService(ResultQueryRepository queryRepository, ParticipantRepository participantRepository,
                              CheckpointRepository checkpointRepository, UserRepository userRepository,
                              RaceAccessGuard raceAccessGuard, ResultLock resultLock,
                              AuditEventService auditEventService, NotificationService notificationService) {
        this.queryRepository = queryRepository;
        this.participantRepository = participantRepository;
        this.checkpointRepository = checkpointRepository;
        this.userRepository = userRepository;
        this.raceAccessGuard = raceAccessGuard;
        this.resultLock = resultLock;
        this.auditEventService = auditEventService;
        this.notificationService = notificationService;
    }

    @Transactional
    public ResultQueryDto submit(UUID raceId, ResultQueryRequest request) {
        User user = currentUser();
        Participant participant = participantRepository.findByRaceIdAndUserId(raceId, user.getId())
            .orElseThrow(() -> new ResourceNotFoundException("You do not have an entry in this race"));
        resultLock.assertOpen(participant.getRace());
        if (queryRepository.countByParticipantIdAndStatus(participant.getId(), ResultQuery.Status.OPEN) >= MAX_OPEN_PER_ENTRY) {
            throw new ConflictException("You already have open result queries for this race", "RESULT_QUERY_LIMIT");
        }
        Checkpoint checkpoint = request.getCheckpointId() == null ? null
            : checkpointRepository.findByRaceIdAndId(raceId, request.getCheckpointId())
                .orElseThrow(() -> new ResourceNotFoundException("Checkpoint not found"));
        ResultQuery query = new ResultQuery();
        query.setRaceId(raceId);
        query.setParticipant(participant);
        query.setSubmittedBy(user.getId());
        query.setCheckpoint(checkpoint);
        query.setMessage(request.getMessage().trim());
        query.setStatus(ResultQuery.Status.OPEN);
        ResultQuery saved = queryRepository.save(query);
        auditEventService.recordInCurrentTransaction("RESULT_QUERY_SUBMITTED", user.getId(), "RESULT_QUERY",
            saved.getId().toString(), "SUCCESS", Map.of("raceId", raceId.toString(), "participantId", participant.getId().toString()));
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<ResultQueryDto> mine() {
        return queryRepository.findBySubmitter(currentUser().getId(), PageRequest.of(0, MAX_LISTED)).stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<ResultQueryDto> open(UUID raceId) {
        raceAccessGuard.assertCanPerformOperationalAction(raceId);
        return queryRepository.findByRaceAndStatus(raceId, ResultQuery.Status.OPEN, PageRequest.of(0, MAX_LISTED)).stream()
            .map(this::toDto).toList();
    }

    @Transactional
    public ResultQueryDto resolve(UUID raceId, UUID queryId, ResultQueryResolution resolution) {
        raceAccessGuard.assertCanPerformOperationalAction(raceId);
        ResultQuery query = queryRepository.findByIdAndRaceIdForUpdate(queryId, raceId)
            .orElseThrow(() -> new ResourceNotFoundException("Result query not found"));
        if (query.getStatus() != ResultQuery.Status.OPEN) {
            throw new ConflictException("This query has already been answered", "RESULT_QUERY_CLOSED");
        }
        User official = currentUser();
        String note = resolution.getNote().trim();
        query.setStatus(resolution.getOutcome() == ResultQueryResolution.Outcome.RESOLVED
            ? ResultQuery.Status.RESOLVED : ResultQuery.Status.REJECTED);
        query.setResolutionNote(note);
        query.setResolvedBy(official.getId());
        query.setResolvedAt(OffsetDateTime.now());
        ResultQuery saved = queryRepository.save(query);
        auditEventService.recordInCurrentTransaction("RESULT_QUERY_" + saved.getStatus().name(), official.getId(),
            "RESULT_QUERY", queryId.toString(), "SUCCESS", Map.of("raceId", raceId.toString(), "note", note));
        if (saved.getSubmittedBy() != null) {
            try {
                notificationService.notifyLocalized(saved.getSubmittedBy(), Notification.NotificationType.RACE_UPDATE,
                    "RESULT_QUERY_" + saved.getStatus().name(), new Object[] {note},
                    Map.of("kind", "RESULT_QUERY", "raceId", raceId.toString(), "resultQueryId", queryId.toString(),
                        "status", saved.getStatus().name()));
            } catch (RuntimeException ignored) {
                // The answer is recorded; notification delivery must not undo it.
            }
        }
        return toDto(saved);
    }

    public long openCount(UUID raceId) {
        return queryRepository.countByRaceIdAndStatus(raceId, ResultQuery.Status.OPEN);
    }

    private ResultQueryDto toDto(ResultQuery query) {
        Participant participant = query.getParticipant();
        Checkpoint checkpoint = query.getCheckpoint();
        return new ResultQueryDto(query.getId(), query.getRaceId(), participant.getId(), participant.getFullName(),
            participant.getBibNumber(), checkpoint == null ? null : checkpoint.getId(),
            checkpoint == null ? null : checkpoint.getName(), query.getMessage(), query.getStatus().name(),
            query.getResolutionNote(), query.getCreatedAt() == null ? null : query.getCreatedAt().toString(),
            query.getResolvedAt() == null ? null : query.getResolvedAt().toString());
    }

    private User currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new UnauthorizedException("Unauthorized");
        }
        return userRepository.findByEmailIgnoreCase(authentication.getName())
            .orElseThrow(() -> new UnauthorizedException("Unauthorized"));
    }
}
