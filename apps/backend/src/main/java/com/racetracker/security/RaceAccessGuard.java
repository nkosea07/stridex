package com.racetracker.security;

import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.model.Race;
import com.racetracker.model.User;
import com.racetracker.repository.RaceRepository;
import com.racetracker.repository.RaceStaffAssignmentRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Enforces per-race ownership for race administrators across controllers. A RACE_ADMIN may
 * only act on a race they created or were assigned to; SUPER_ADMIN bypasses the check.
 */
@Component
public class RaceAccessGuard {

    private final RaceRepository raceRepository;
    private final RaceStaffAssignmentRepository raceStaffAssignmentRepository;

    public RaceAccessGuard(RaceRepository raceRepository, RaceStaffAssignmentRepository raceStaffAssignmentRepository) {
        this.raceRepository = raceRepository;
        this.raceStaffAssignmentRepository = raceStaffAssignmentRepository;
    }

    public void assertCanRead(UUID raceId) {
        Race race = raceRepository.findByIdForAuthorization(raceId)
            .orElseThrow(() -> new ResourceNotFoundException("Race not found"));
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!isAuthenticated(authentication)) {
            throw new AccessDeniedException("You do not have access to this race");
        }
        if (hasRole(authentication, "ROLE_SUPER_ADMIN")) {
            return;
        }
        if (hasRole(authentication, "ROLE_RACE_ADMIN")) {
            assertCanManage(race, authentication);
            return;
        }
        if (hasRole(authentication, "ROLE_ATHLETE") || hasRole(authentication, "ROLE_SPECTATOR")) {
            if (!isPublicRace(race)) {
                throw new ResourceNotFoundException("Race not found");
            }
            return;
        }
        if (hasRole(authentication, "ROLE_OFFICIAL") || hasRole(authentication, "ROLE_VOLUNTEER")) {
            assertCanReadStaff(race, authentication);
            return;
        }
        throw new AccessDeniedException("You do not have access to this race");
    }

    /**
     * Authorizes race-level reads for assigned staff only. Checkpoint volunteers
     * without a race_staff_assignments row are deliberately excluded here.
     */
    public void assertCanReadStaff(UUID raceId) {
        Race race = raceRepository.findByIdForAuthorization(raceId)
            .orElseThrow(() -> new ResourceNotFoundException("Race not found"));
        assertCanReadStaff(race, SecurityContextHolder.getContext().getAuthentication());
    }

    /**
     * Authorizes an operation on one checkpoint. A volunteer with only a
     * checkpoint_volunteers row may use this seam for that checkpoint, but may
     * not use race-level endpoints.
     */
    public void assertCanAccessCheckpoint(UUID raceId, UUID checkpointId) {
        Race race = raceRepository.findByIdForAuthorization(raceId)
            .orElseThrow(() -> new ResourceNotFoundException("Race not found"));
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!isAuthenticated(authentication)) {
            throw new AccessDeniedException("You do not have access to this checkpoint");
        }
        if (hasRole(authentication, "ROLE_SUPER_ADMIN") || hasRole(authentication, "ROLE_RACE_ADMIN")) {
            assertCanManage(race, authentication);
            return;
        }
        if (hasRole(authentication, "ROLE_OFFICIAL")
            && raceStaffAssignmentRepository.existsActiveByRaceIdAndUserEmailAndStaffRole(
                raceId, authentication.getName(), User.UserRole.OFFICIAL)) {
            return;
        }
        if (hasRole(authentication, "ROLE_VOLUNTEER")) {
            boolean raceAssigned = raceStaffAssignmentRepository.existsActiveByRaceIdAndUserEmailAndStaffRole(
                raceId, authentication.getName(), User.UserRole.VOLUNTEER);
            boolean checkpointAssigned = raceStaffAssignmentRepository
                .existsCheckpointVolunteerByRaceIdAndCheckpointIdAndUserEmail(
                    raceId, checkpointId, authentication.getName());
            if (raceAssigned || checkpointAssigned) {
                return;
            }
        }
        throw new AccessDeniedException("You do not have access to this checkpoint");
    }

    public Race requireManagedRace(UUID raceId) {
        Race race = raceRepository.findByIdForAuthorization(raceId)
            .orElseThrow(() -> new ResourceNotFoundException("Race not found"));
        assertCanManage(race);
        return race;
    }

    public void assertCanManage(UUID raceId) {
        Race race = raceRepository.findByIdForAuthorization(raceId)
            .orElseThrow(() -> new ResourceNotFoundException("Race not found"));
        assertCanManage(race);
    }

    public void assertCanManage(Race race) {
        assertCanManage(race, SecurityContextHolder.getContext().getAuthentication());
    }

    /**
     * Service-layer seam for race-scoped mutations where the caller is resolved
     * from an explicit actor email rather than the security context.
     */
    public void assertCanManage(Race race, User actor) {
        if (actor == null) {
            throw new AccessDeniedException("You do not have access to this race");
        }
        assertCanManage(race, actor.getEmail(), actor.getRole());
    }

    /**
     * Allows operational participant/checkpoint actions for assigned staff.
     * Checkpoint-only volunteers must use the checkpoint-scoped guard instead.
     */
    public void assertCanPerformOperationalAction(UUID raceId) {
        Race race = raceRepository.findByIdForAuthorization(raceId)
            .orElseThrow(() -> new ResourceNotFoundException("Race not found"));
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!isAuthenticated(authentication)) {
            throw new AccessDeniedException("You do not have access to this race");
        }
        if (hasRole(authentication, "ROLE_SUPER_ADMIN") || hasRole(authentication, "ROLE_RACE_ADMIN")) {
            assertCanManage(race, authentication);
            return;
        }
        if (hasRole(authentication, "ROLE_OFFICIAL")
            && raceStaffAssignmentRepository.existsActiveByRaceIdAndUserEmailAndStaffRole(
                raceId, authentication.getName(), User.UserRole.OFFICIAL)) {
            return;
        }
        if (hasRole(authentication, "ROLE_VOLUNTEER")
            && raceStaffAssignmentRepository.existsActiveByRaceIdAndUserEmailAndStaffRole(
                raceId, authentication.getName(), User.UserRole.VOLUNTEER)) {
            return;
        }
        throw new AccessDeniedException("You do not have access to this race");
    }

    public void assertCanImportTiming(UUID raceId) {
        Race race = raceRepository.findByIdForAuthorization(raceId)
            .orElseThrow(() -> new ResourceNotFoundException("Race not found"));
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!isAuthenticated(authentication)) {
            throw new AccessDeniedException("You do not have access to this race");
        }
        if (hasRole(authentication, "ROLE_SUPER_ADMIN") || hasRole(authentication, "ROLE_RACE_ADMIN")) {
            assertCanManage(race, authentication);
            return;
        }
        if (hasRole(authentication, "ROLE_OFFICIAL")
            && raceStaffAssignmentRepository.existsActiveByRaceIdAndUserEmailAndStaffRole(
                raceId, authentication.getName(), User.UserRole.OFFICIAL)) {
            return;
        }
        throw new AccessDeniedException("You do not have access to this race");
    }

    /**
     * Allows only race-management staff to subscribe to a race's private live feed.
     * Unlike the controller guard, this method is intentionally not permissive for
     * participant and spectator roles because a WebSocket subscription has no HTTP
     * resource-level authorization boundary after it is established.
     */
    public void assertCanSubscribeToRace(UUID raceId, Authentication authentication) {
        if (!isAuthenticated(authentication)
            || (!hasRole(authentication, "ROLE_SUPER_ADMIN")
                && !hasRole(authentication, "ROLE_RACE_ADMIN")
                && !hasRole(authentication, "ROLE_OFFICIAL")
                && !hasRole(authentication, "ROLE_VOLUNTEER"))) {
            throw new AccessDeniedException("You do not have access to this race");
        }

        Race race = raceRepository.findByIdForAuthorization(raceId)
            .orElseThrow(() -> new AccessDeniedException("You do not have access to this race"));
        assertCanReadStaff(race, authentication);
    }

    private void assertCanManage(Race race, Authentication authentication) {
        if (!isAuthenticated(authentication)) {
            throw new AccessDeniedException("You do not have access to this race");
        }
        User.UserRole role = null;
        if (hasRole(authentication, "ROLE_SUPER_ADMIN")) {
            role = User.UserRole.SUPER_ADMIN;
        } else if (hasRole(authentication, "ROLE_RACE_ADMIN")) {
            role = User.UserRole.RACE_ADMIN;
        }
        assertCanManage(race, authentication.getName(), role);
    }

    private void assertCanManage(Race race, String username, User.UserRole role) {
        if (role == User.UserRole.SUPER_ADMIN) {
            return;
        }
        if (role == User.UserRole.RACE_ADMIN) {
            User owner = race.getCreatedBy();
            boolean legacyUnowned = owner == null && (race.getAdmins() == null || race.getAdmins().isEmpty());
            if (legacyUnowned) {
                throw new AccessDeniedException("You do not have access to this race");
            }
            boolean owns = owner != null && username.equalsIgnoreCase(owner.getEmail());
            boolean assigned = race.getAdmins() != null && race.getAdmins().stream()
                .anyMatch(admin -> username.equalsIgnoreCase(admin.getEmail()));
            if (!owns && !assigned) {
                throw new AccessDeniedException("You do not have access to this race");
            }
            return;
        }

        throw new AccessDeniedException("You do not have access to this race");
    }

    private void assertCanReadStaff(Race race, Authentication authentication) {
        if (!isAuthenticated(authentication)) {
            throw new AccessDeniedException("You do not have access to this race");
        }
        if (hasRole(authentication, "ROLE_SUPER_ADMIN")) {
            return;
        }
        if (hasRole(authentication, "ROLE_RACE_ADMIN")) {
            assertCanManage(race, authentication);
            return;
        }
        if (hasRole(authentication, "ROLE_OFFICIAL")
            && raceStaffAssignmentRepository.existsActiveByRaceIdAndUserEmailAndStaffRole(
                race.getId(), authentication.getName(), User.UserRole.OFFICIAL)) {
            return;
        }
        if (hasRole(authentication, "ROLE_VOLUNTEER")
            && raceStaffAssignmentRepository.existsActiveByRaceIdAndUserEmailAndStaffRole(
                race.getId(), authentication.getName(), User.UserRole.VOLUNTEER)) {
            return;
        }
        throw new AccessDeniedException("You do not have access to this race");
    }

    private boolean isAuthenticated(Authentication authentication) {
        return authentication != null
            && authentication.isAuthenticated()
            && !(authentication.getPrincipal() instanceof String principal && "anonymousUser".equals(principal));
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream()
            .anyMatch(authority -> role.equals(authority.getAuthority()));
    }

    private boolean isPublicRace(Race race) {
        return com.racetracker.service.PublicRaceVisibility.isVisible(race);
    }
}
