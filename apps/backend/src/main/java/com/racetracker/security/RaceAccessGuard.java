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
 * only act on a race they created or were assigned to; SUPER_ADMIN bypasses the check, and
 * unowned legacy races (no creator, no assigned admins) stay accessible to avoid lockouts.
 */
@Component
public class RaceAccessGuard {

    private final RaceRepository raceRepository;

    public RaceAccessGuard(RaceRepository raceRepository) {
        this.raceRepository = raceRepository;
    }

    public Race requireManagedRace(UUID raceId) {
        Race race = raceRepository.findById(raceId)
            .orElseThrow(() -> new ResourceNotFoundException("Race not found"));
        assertCanManage(race);
        return race;
    }

    public void assertCanManage(UUID raceId) {
        Race race = raceRepository.findById(raceId)
            .orElseThrow(() -> new ResourceNotFoundException("Race not found"));
        assertCanManage(race);
    }

    public void assertCanManage(Race race) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return;
        }
        boolean privilegedGlobal = hasRole(authentication, "ROLE_SUPER_ADMIN");
        boolean raceAdmin = hasRole(authentication, "ROLE_RACE_ADMIN");
        if (privilegedGlobal || !raceAdmin) {
            return;
        }
        User owner = race.getCreatedBy();
        boolean legacyUnowned = owner == null && (race.getAdmins() == null || race.getAdmins().isEmpty());
        if (legacyUnowned) {
            return;
        }
        String username = authentication.getName();
        boolean owns = owner != null && username.equalsIgnoreCase(owner.getEmail());
        boolean assigned = race.getAdmins() != null && race.getAdmins().stream()
            .anyMatch(admin -> username.equalsIgnoreCase(admin.getEmail()));
        if (!owns && !assigned) {
            throw new AccessDeniedException("You do not have access to this race");
        }
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream()
            .anyMatch(authority -> role.equals(authority.getAuthority()));
    }
}
