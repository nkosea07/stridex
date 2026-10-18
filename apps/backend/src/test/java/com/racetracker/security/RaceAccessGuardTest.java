package com.racetracker.security;

import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.model.Race;
import com.racetracker.model.User;
import com.racetracker.repository.RaceRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RaceAccessGuardTest {

    @Mock RaceRepository raceRepository;

    private final UUID raceId = UUID.randomUUID();

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(String email, String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            email, null, List.of(new SimpleGrantedAuthority(role))));
    }

    private Race ownedRace(String ownerEmail) {
        Race race = new Race();
        race.setId(raceId);
        race.setCreatedBy(User.builder().id(UUID.randomUUID()).email(ownerEmail).build());
        return race;
    }

    @Test
    void raceAdminCannotManageAnotherOrganizersRace() {
        when(raceRepository.findByIdForAuthorization(raceId)).thenReturn(Optional.of(ownedRace("owner@example.com")));
        authenticate("other@example.com", "ROLE_RACE_ADMIN");

        RaceAccessGuard guard = new RaceAccessGuard(raceRepository);
        assertThatThrownBy(() -> guard.assertCanManage(raceId))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void ownerWithRaceAdminRoleIsAllowed() {
        when(raceRepository.findByIdForAuthorization(raceId)).thenReturn(Optional.of(ownedRace("owner@example.com")));
        authenticate("owner@example.com", "ROLE_RACE_ADMIN");

        RaceAccessGuard guard = new RaceAccessGuard(raceRepository);
        assertThatCode(() -> guard.assertCanManage(raceId)).doesNotThrowAnyException();
    }

    @Test
    void assignedRaceAdminIsAllowed() {
        Race race = ownedRace("owner@example.com");
        race.setAdmins(Set.of(User.builder().id(UUID.randomUUID()).email("assigned@example.com").build()));
        when(raceRepository.findByIdForAuthorization(raceId)).thenReturn(Optional.of(race));
        authenticate("assigned@example.com", "ROLE_RACE_ADMIN");

        RaceAccessGuard guard = new RaceAccessGuard(raceRepository);
        assertThatCode(() -> guard.assertCanManage(raceId)).doesNotThrowAnyException();
    }

    @Test
    void superAdminBypassesOwnership() {
        when(raceRepository.findByIdForAuthorization(raceId)).thenReturn(Optional.of(ownedRace("owner@example.com")));
        authenticate("admin@example.com", "ROLE_SUPER_ADMIN");

        RaceAccessGuard guard = new RaceAccessGuard(raceRepository);
        assertThatCode(() -> guard.assertCanManage(raceId)).doesNotThrowAnyException();
    }

    @Test
    void raceAdminCannotManageLegacyUnownedRace() {
        Race race = new Race();
        race.setId(raceId);
        when(raceRepository.findByIdForAuthorization(raceId)).thenReturn(Optional.of(race));
        authenticate("anyone@example.com", "ROLE_RACE_ADMIN");

        RaceAccessGuard guard = new RaceAccessGuard(raceRepository);
        assertThatThrownBy(() -> guard.assertCanManage(raceId))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void superAdminCanManageLegacyUnownedRace() {
        Race race = new Race();
        race.setId(raceId);
        when(raceRepository.findByIdForAuthorization(raceId)).thenReturn(Optional.of(race));
        authenticate("admin@example.com", "ROLE_SUPER_ADMIN");

        RaceAccessGuard guard = new RaceAccessGuard(raceRepository);
        assertThatCode(() -> guard.assertCanManage(raceId)).doesNotThrowAnyException();
    }

    @Test
    void unownedRaceWithAssignedAdminsDeniesUnassignedRaceAdmin() {
        Race race = new Race();
        race.setId(raceId);
        race.setAdmins(Set.of(User.builder().id(UUID.randomUUID()).email("assigned@example.com").build()));
        when(raceRepository.findByIdForAuthorization(raceId)).thenReturn(Optional.of(race));
        authenticate("other@example.com", "ROLE_RACE_ADMIN");

        RaceAccessGuard guard = new RaceAccessGuard(raceRepository);
        assertThatThrownBy(() -> guard.assertCanManage(raceId))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void unownedRaceWithAssignedAdminsAllowsAssignedRaceAdmin() {
        Race race = new Race();
        race.setId(raceId);
        race.setAdmins(Set.of(User.builder().id(UUID.randomUUID()).email("assigned@example.com").build()));
        when(raceRepository.findByIdForAuthorization(raceId)).thenReturn(Optional.of(race));
        authenticate("assigned@example.com", "ROLE_RACE_ADMIN");

        RaceAccessGuard guard = new RaceAccessGuard(raceRepository);
        assertThatCode(() -> guard.assertCanManage(raceId)).doesNotThrowAnyException();
    }

    @Test
    void nonRaceAdminRoleIsNotRestrictedByRaceOwnership() {
        when(raceRepository.findByIdForAuthorization(raceId)).thenReturn(Optional.of(ownedRace("owner@example.com")));
        authenticate("athlete@example.com", "ROLE_ATHLETE");

        RaceAccessGuard guard = new RaceAccessGuard(raceRepository);
        assertThatCode(() -> guard.assertCanManage(raceId)).doesNotThrowAnyException();
    }

    @Test
    void missingRaceRaisesNotFound() {
        when(raceRepository.findByIdForAuthorization(raceId)).thenReturn(Optional.empty());
        RaceAccessGuard guard = new RaceAccessGuard(raceRepository);

        assertThatThrownBy(() -> guard.assertCanManage(raceId))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void unauthenticatedCallerCannotManageRace() {
        when(raceRepository.findByIdForAuthorization(raceId))
            .thenReturn(Optional.of(ownedRace("owner@example.com")));

        RaceAccessGuard guard = new RaceAccessGuard(raceRepository);

        assertThatThrownBy(() -> guard.assertCanManage(raceId))
            .isInstanceOf(AccessDeniedException.class);
    }
}
