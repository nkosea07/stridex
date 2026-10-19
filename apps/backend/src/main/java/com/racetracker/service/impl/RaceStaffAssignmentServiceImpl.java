package com.racetracker.service.impl;

import com.racetracker.dto.staff.RaceStaffAssignmentDto;
import com.racetracker.dto.staff.RaceStaffAssignmentRequest;
import com.racetracker.exception.ApiException;
import com.racetracker.exception.ConflictException;
import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.exception.UnauthorizedException;
import com.racetracker.model.Race;
import com.racetracker.model.RaceStaffAssignment;
import com.racetracker.model.StaffRole;
import com.racetracker.model.User;
import com.racetracker.repository.RaceRepository;
import com.racetracker.repository.RaceStaffAssignmentRepository;
import com.racetracker.repository.UserRepository;
import com.racetracker.security.RaceAccessGuard;
import com.racetracker.service.AuditEventService;
import com.racetracker.service.RaceStaffAssignmentService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Service
public class RaceStaffAssignmentServiceImpl implements RaceStaffAssignmentService {

    private final UserRepository userRepository;
    private final RaceRepository raceRepository;
    private final RaceStaffAssignmentRepository assignmentRepository;
    private final AuditEventService auditEventService;
    private final RaceAccessGuard raceAccessGuard;

    public RaceStaffAssignmentServiceImpl(
        UserRepository userRepository,
        RaceRepository raceRepository,
        RaceStaffAssignmentRepository assignmentRepository,
        AuditEventService auditEventService,
        RaceAccessGuard raceAccessGuard
    ) {
        this.userRepository = userRepository;
        this.raceRepository = raceRepository;
        this.assignmentRepository = assignmentRepository;
        this.auditEventService = auditEventService;
        this.raceAccessGuard = raceAccessGuard;
    }

    @Override
    @Transactional
    public void promote(UUID userId, StaffRole role, String actorEmail) {
        User actor = resolveActor(actorEmail);
        if (actor.getRole() != User.UserRole.SUPER_ADMIN) {
            throw new AccessDeniedException("Only a super admin may promote race staff");
        }
        User target = userRepository.findByIdForUpdate(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        requireVerified(target);

        User.UserRole targetRole = role.toUserRole();
        if (target.getRole() == targetRole) {
            throw new ConflictException("User already has the requested staff role", "STAFF_ROLE_ALREADY_SET");
        }
        if (target.getRole() == User.UserRole.SUPER_ADMIN || target.getRole() == User.UserRole.RACE_ADMIN) {
            throw new ConflictException("Privileged users cannot be promoted to race staff", "STAFF_ROLE_NOT_ELIGIBLE");
        }
        User.UserRole otherStaffRole = targetRole == User.UserRole.OFFICIAL
            ? User.UserRole.VOLUNTEER
            : User.UserRole.OFFICIAL;
        if (target.getRole() == User.UserRole.OFFICIAL || target.getRole() == User.UserRole.VOLUNTEER) {
            otherStaffRole = target.getRole();
        }
        if (assignmentRepository.existsByUserIdAndStaffRoleAndActiveTrue(target.getId(), otherStaffRole)) {
            throw new ConflictException(
                "User has active race assignments under the other staff role",
                "STAFF_ROLE_ASSIGNMENTS_CONFLICT");
        }

        target.setRole(targetRole);
        userRepository.save(target);
        auditEventService.recordInCurrentTransaction("STAFF_ROLE_PROMOTED", actor.getId(),
            "USER", target.getId().toString(), "SUCCESS", Map.of("role", targetRole.name()));
    }

    @Override
    @Transactional
    public RaceStaffAssignmentDto assign(UUID raceId, RaceStaffAssignmentRequest request, String actorEmail) {
        Race race = raceRepository.findByIdForUpdate(raceId)
            .orElseThrow(() -> new ResourceNotFoundException("Race not found"));
        User actor = requireRaceManager(actorEmail);
        raceAccessGuard.assertCanManage(race, actor);
        User target = userRepository.findByIdForUpdate(request.getUserId())
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        requireVerified(target);

        User.UserRole expectedRole = request.getRole().toUserRole();
        if (target.getRole() != expectedRole) {
            throw new ApiException("Target user must have the requested staff role", "STAFF_ROLE_REQUIRED");
        }
        if (assignmentRepository.findFirstByRaceIdAndUserIdAndActiveTrue(raceId, target.getId()).isPresent()) {
            throw new ConflictException("User is already assigned to this race", "RACE_STAFF_ALREADY_ASSIGNED");
        }

        RaceStaffAssignment assignment = RaceStaffAssignment.builder()
            .race(race)
            .user(target)
            .staffRole(expectedRole)
            .active(true)
            .assignedAt(OffsetDateTime.now())
            .assignedBy(actor)
            .build();
        RaceStaffAssignment saved = assignmentRepository.save(assignment);
        auditEventService.recordInCurrentTransaction("RACE_STAFF_ASSIGNED", actor.getId(), "RACE",
            raceId.toString(), "SUCCESS", Map.of("assignedUserId", target.getId().toString(),
                "staffRole", expectedRole.name()));
        return toDto(saved);
    }

    @Override
    @Transactional
    public void revoke(UUID raceId, UUID userId, String actorEmail) {
        Race race = raceRepository.findByIdForUpdate(raceId)
            .orElseThrow(() -> new ResourceNotFoundException("Race not found"));
        User actor = requireRaceManager(actorEmail);
        raceAccessGuard.assertCanManage(race, actor);
        RaceStaffAssignment assignment = assignmentRepository
            .findFirstByRaceIdAndUserIdAndActiveTrue(raceId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Active race staff assignment not found"));

        assignment.setActive(false);
        assignment.setRevokedAt(OffsetDateTime.now());
        assignment.setRevokedBy(actor);
        assignmentRepository.save(assignment);
        auditEventService.recordInCurrentTransaction("RACE_STAFF_REVOKED", actor.getId(), "RACE",
            raceId.toString(), "SUCCESS", Map.of("revokedUserId", userId.toString(),
                "staffRole", assignment.getStaffRole().name()));
    }

    private User resolveActor(String actorEmail) {
        return userRepository.findByEmailIgnoreCase(actorEmail)
            .orElseThrow(() -> new UnauthorizedException("Unauthorized"));
    }

    private User requireRaceManager(String actorEmail) {
        User actor = resolveActor(actorEmail);
        if (actor.getRole() != User.UserRole.SUPER_ADMIN && actor.getRole() != User.UserRole.RACE_ADMIN) {
            throw new AccessDeniedException("Only race managers may manage race staff");
        }
        return actor;
    }

    private void requireVerified(User target) {
        if (!Boolean.TRUE.equals(target.getEmailVerified()) || !Boolean.TRUE.equals(target.getIsActive())) {
            throw new ConflictException("Staff user must be active and email verified", "STAFF_USER_NOT_VERIFIED");
        }
    }

    private RaceStaffAssignmentDto toDto(RaceStaffAssignment assignment) {
        return new RaceStaffAssignmentDto(
            assignment.getId(),
            assignment.getRace().getId(),
            assignment.getUser().getId(),
            assignment.getStaffRole(),
            Boolean.TRUE.equals(assignment.getActive()),
            assignment.getAssignedAt(),
            assignment.getRevokedAt()
        );
    }
}
