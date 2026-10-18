package com.racetracker.security;

import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.model.Race;
import com.racetracker.model.User;
import com.racetracker.repository.RaceRepository;
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

    public RaceAccessGuard(RaceRepository raceRepository) {
        this.raceRepository = raceRepository;
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
        if (hasRole(authentication, "ROLE_SUPER_ADMIN")
            || hasRole(authentication, "ROLE_RACE_ADMIN")) {
            assertCanManage(race, authentication);
        }
    }

    private void assertCanManage(Race race, Authentication authentication) {
        if (!isAuthenticated(authentication)) {
            throw new AccessDeniedException("You do not have access to this race");
        }
        boolean privilegedGlobal = hasRole(authentication, "ROLE_SUPER_ADMIN");
        boolean raceAdmin = hasRole(authentication, "ROLE_RACE_ADMIN");
        if (privilegedGlobal || !raceAdmin) {
            return;
        }
        User owner = race.getCreatedBy();
        boolean legacyUnowned = owner == null && (race.getAdmins() == null || race.getAdmins().isEmpty());
        if (legacyUnowned) {
            throw new AccessDeniedException("You do not have access to this race");
        }
        String username = authentication.getName();
        boolean owns = owner != null && username.equalsIgnoreCase(owner.getEmail());
        boolean assigned = race.getAdmins() != null && race.getAdmins().stream()
            .anyMatch(admin -> username.equalsIgnoreCase(admin.getEmail()));
        if (!owns && !assigned) {
            throw new AccessDeniedException("You do not have access to this race");
        }
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
}
