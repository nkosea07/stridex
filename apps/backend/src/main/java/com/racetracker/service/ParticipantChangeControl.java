package com.racetracker.service;

import com.racetracker.exception.ApiException;
import com.racetracker.exception.ConflictException;
import com.racetracker.model.Participant;
import com.racetracker.model.Race;
import com.racetracker.repository.CheckpointRecordRepository;
import com.racetracker.repository.ParticipantPenaltyRepository;
import com.racetracker.repository.RaceResultRepository;
import com.racetracker.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Guards and records staff changes to participant details that decide results: category, date of
 * birth (age group), gender, bib, tag, team, and status. Those changes are blocked once results are
 * certified, official status decisions need a reason, and every change is written to the audit log.
 * Participants with timing data cannot be deleted; they are withdrawn (DNS) instead.
 */
@Component
public class ParticipantChangeControl {

    public record Snapshot(UUID categoryId, UUID waveId, LocalDate dateOfBirth, String gender, String bibNumber,
                           String rfidTag, String teamName, String clubName, Participant.ParticipantStatus status) {
    }

    private static final Set<Participant.ParticipantStatus> OFFICIAL_DECISIONS = EnumSet.of(
        Participant.ParticipantStatus.DISQUALIFIED,
        Participant.ParticipantStatus.DNF,
        Participant.ParticipantStatus.DNS,
        Participant.ParticipantStatus.FINISHED
    );

    private final ResultLock resultLock;
    private final AuditEventService auditEventService;
    private final UserRepository userRepository;
    private final CheckpointRecordRepository checkpointRecordRepository;
    private final ParticipantPenaltyRepository penaltyRepository;
    private final RaceResultRepository raceResultRepository;

    public ParticipantChangeControl(ResultLock resultLock, AuditEventService auditEventService,
                                    UserRepository userRepository, CheckpointRecordRepository checkpointRecordRepository,
                                    ParticipantPenaltyRepository penaltyRepository, RaceResultRepository raceResultRepository) {
        this.resultLock = resultLock;
        this.auditEventService = auditEventService;
        this.userRepository = userRepository;
        this.checkpointRecordRepository = checkpointRecordRepository;
        this.penaltyRepository = penaltyRepository;
        this.raceResultRepository = raceResultRepository;
    }

    public Snapshot snapshot(Participant participant) {
        return new Snapshot(
            participant.getCategory() == null ? null : participant.getCategory().getId(),
            participant.getWave() == null ? null : participant.getWave().getId(),
            participant.getDateOfBirth(),
            participant.getGender() == null ? null : participant.getGender().name(),
            participant.getBibNumber(),
            participant.getRfidTag(),
            participant.getTeamName(),
            participant.getClubName(),
            participant.getStatus());
    }

    public void verifyAndRecord(Snapshot before, Participant after, String statusReason) {
        Snapshot current = snapshot(after);
        Map<String, Object> changes = new LinkedHashMap<>();
        compare(changes, "categoryId", before.categoryId(), current.categoryId(), true);
        compare(changes, "waveId", before.waveId(), current.waveId(), true);
        compare(changes, "dateOfBirth", before.dateOfBirth(), current.dateOfBirth(), false);
        compare(changes, "gender", before.gender(), current.gender(), false);
        compare(changes, "bibNumber", before.bibNumber(), current.bibNumber(), true);
        compare(changes, "rfidTag", before.rfidTag(), current.rfidTag(), true);
        compare(changes, "teamName", before.teamName(), current.teamName(), true);
        compare(changes, "clubName", before.clubName(), current.clubName(), true);
        compare(changes, "status", before.status(), current.status(), true);
        if (changes.isEmpty()) {
            return;
        }
        resultLock.assertOpen(after.getRace());

        if (before.status() != current.status() && current.status() != null && OFFICIAL_DECISIONS.contains(current.status())) {
            String reason = statusReason == null ? "" : statusReason.trim();
            if (reason.length() < 3) {
                throw new ApiException("A reason is required when setting an official status such as "
                    + current.status().name(), "STATUS_REASON_REQUIRED");
            }
            after.setStatusReason(reason);
            changes.put("statusReason", reason);
        }
        auditEventService.recordInCurrentTransaction("PARTICIPANT_RESULT_DETAILS_CHANGED", actorId(), "PARTICIPANT",
            after.getId().toString(), "SUCCESS", changes);
    }

    public void verifyDeletable(Participant participant) {
        Race race = participant.getRace();
        resultLock.assertOpen(race);
        boolean hasTiming = checkpointRecordRepository.existsIncludingVoidedByParticipantId(participant.getId())
            || penaltyRepository.existsByParticipantId(participant.getId())
            || raceResultRepository.existsByParticipantId(participant.getId());
        boolean raceUnderway = race != null && (race.getStatus() == Race.RaceStatus.IN_PROGRESS
            || race.getStatus() == Race.RaceStatus.COMPLETED);
        if (hasTiming || raceUnderway) {
            throw new ConflictException(
                "This entry has timing data or the race has started. Mark it DNS or withdrawn instead of deleting it.",
                "PARTICIPANT_HAS_TIMING_DATA");
        }
    }

    public void recordDeletion(Participant participant) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("raceId", participant.getRace() == null ? null : participant.getRace().getId().toString());
        details.put("bibNumber", participant.getBibNumber());
        auditEventService.recordInCurrentTransaction("PARTICIPANT_DELETED", actorId(), "PARTICIPANT",
            participant.getId().toString(), "SUCCESS", details);
    }

    private void compare(Map<String, Object> changes, String field, Object before, Object after, boolean recordValues) {
        if (Objects.equals(before, after)) {
            return;
        }
        if (recordValues) {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("from", before == null ? null : before.toString());
            values.put("to", after == null ? null : after.toString());
            changes.put(field, values);
        } else {
            changes.put(field, "changed");
        }
    }

    private UUID actorId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        return userRepository.findByEmailIgnoreCase(authentication.getName()).map(com.racetracker.model.User::getId).orElse(null);
    }
}
