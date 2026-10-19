package com.racetracker.security;

import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.model.Race;
import com.racetracker.model.User;
import com.racetracker.repository.RaceRepository;
import com.racetracker.repository.RaceStaffAssignmentRepository;
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
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RaceAccessGuardTest {

    @Mock RaceRepository raceRepository;
    @Mock RaceStaffAssignmentRepository raceStaffAssignmentRepository;

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

        RaceAccessGuard guard = new RaceAccessGuard(raceRepository, raceStaffAssignmentRepository);
        assertThatThrownBy(() -> guard.assertCanManage(raceId))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void raceAdminCannotReadAnotherOrganizersRace() {
        when(raceRepository.findByIdForAuthorization(raceId)).thenReturn(Optional.of(ownedRace("owner@example.com")));
        authenticate("other@example.com", "ROLE_RACE_ADMIN");

        RaceAccessGuard guard = new RaceAccessGuard(raceRepository, raceStaffAssignmentRepository);
        assertThatThrownBy(() -> guard.assertCanRead(raceId))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void ownerWithRaceAdminRoleIsAllowed() {
        when(raceRepository.findByIdForAuthorization(raceId)).thenReturn(Optional.of(ownedRace("owner@example.com")));
        authenticate("owner@example.com", "ROLE_RACE_ADMIN");

        RaceAccessGuard guard = new RaceAccessGuard(raceRepository, raceStaffAssignmentRepository);
        assertThatCode(() -> guard.assertCanManage(raceId)).doesNotThrowAnyException();
    }

    @Test
    void assignedRaceAdminIsAllowed() {
        Race race = ownedRace("owner@example.com");
        race.setAdmins(Set.of(User.builder().id(UUID.randomUUID()).email("assigned@example.com").build()));
        when(raceRepository.findByIdForAuthorization(raceId)).thenReturn(Optional.of(race));
        authenticate("assigned@example.com", "ROLE_RACE_ADMIN");

        RaceAccessGuard guard = new RaceAccessGuard(raceRepository, raceStaffAssignmentRepository);
        assertThatCode(() -> guard.assertCanManage(raceId)).doesNotThrowAnyException();
    }

    @Test
    void superAdminBypassesOwnership() {
        when(raceRepository.findByIdForAuthorization(raceId)).thenReturn(Optional.of(ownedRace("owner@example.com")));
        authenticate("admin@example.com", "ROLE_SUPER_ADMIN");

        RaceAccessGuard guard = new RaceAccessGuard(raceRepository, raceStaffAssignmentRepository);
        assertThatCode(() -> guard.assertCanManage(raceId)).doesNotThrowAnyException();
    }

    @Test
    void raceAdminCannotManageLegacyUnownedRace() {
        Race race = new Race();
        race.setId(raceId);
        when(raceRepository.findByIdForAuthorization(raceId)).thenReturn(Optional.of(race));
        authenticate("anyone@example.com", "ROLE_RACE_ADMIN");

        RaceAccessGuard guard = new RaceAccessGuard(raceRepository, raceStaffAssignmentRepository);
        assertThatThrownBy(() -> guard.assertCanManage(raceId))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void superAdminCanManageLegacyUnownedRace() {
        Race race = new Race();
        race.setId(raceId);
        when(raceRepository.findByIdForAuthorization(raceId)).thenReturn(Optional.of(race));
        authenticate("admin@example.com", "ROLE_SUPER_ADMIN");

        RaceAccessGuard guard = new RaceAccessGuard(raceRepository, raceStaffAssignmentRepository);
        assertThatCode(() -> guard.assertCanManage(raceId)).doesNotThrowAnyException();
    }

    @Test
    void unownedRaceWithAssignedAdminsDeniesUnassignedRaceAdmin() {
        Race race = new Race();
        race.setId(raceId);
        race.setAdmins(Set.of(User.builder().id(UUID.randomUUID()).email("assigned@example.com").build()));
        when(raceRepository.findByIdForAuthorization(raceId)).thenReturn(Optional.of(race));
        authenticate("other@example.com", "ROLE_RACE_ADMIN");

        RaceAccessGuard guard = new RaceAccessGuard(raceRepository, raceStaffAssignmentRepository);
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

        RaceAccessGuard guard = new RaceAccessGuard(raceRepository, raceStaffAssignmentRepository);
        assertThatCode(() -> guard.assertCanManage(raceId)).doesNotThrowAnyException();
    }

    @Test
    void normalRoleCannotManageRace() {
        when(raceRepository.findByIdForAuthorization(raceId)).thenReturn(Optional.of(ownedRace("owner@example.com")));
        authenticate("athlete@example.com", "ROLE_ATHLETE");

        RaceAccessGuard guard = new RaceAccessGuard(raceRepository, raceStaffAssignmentRepository);
        assertThatThrownBy(() -> guard.assertCanManage(raceId))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void assignedOfficialCanReadButCannotManageRace() {
        Race assignedRace = ownedRace("owner@example.com");
        when(raceRepository.findByIdForAuthorization(raceId)).thenReturn(Optional.of(assignedRace));
        when(raceStaffAssignmentRepository.existsActiveByRaceIdAndUserEmailAndStaffRole(
            raceId, "official@example.com", User.UserRole.OFFICIAL)).thenReturn(true);
        authenticate("official@example.com", "ROLE_OFFICIAL");

        RaceAccessGuard guard = new RaceAccessGuard(raceRepository, raceStaffAssignmentRepository);
        assertThatCode(() -> guard.assertCanReadStaff(raceId)).doesNotThrowAnyException();
        assertThatThrownBy(() -> guard.assertCanManage(raceId))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void assignedVolunteerCanReadCheckpointButCannotManageRace() {
        UUID checkpointId = UUID.randomUUID();
        when(raceRepository.findByIdForAuthorization(raceId)).thenReturn(Optional.of(ownedRace("owner@example.com")));
        when(raceStaffAssignmentRepository.existsActiveByRaceIdAndUserEmailAndStaffRole(
            raceId, "volunteer@example.com", User.UserRole.VOLUNTEER)).thenReturn(true);
        authenticate("volunteer@example.com", "ROLE_VOLUNTEER");

        RaceAccessGuard guard = new RaceAccessGuard(raceRepository, raceStaffAssignmentRepository);
        assertThatCode(() -> guard.assertCanReadStaff(raceId)).doesNotThrowAnyException();
        assertThatCode(() -> guard.assertCanAccessCheckpoint(raceId, checkpointId)).doesNotThrowAnyException();
        assertThatThrownBy(() -> guard.assertCanManage(raceId))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void assignedOfficialCanPerformRaceLevelOperationalAction() {
        when(raceRepository.findByIdForAuthorization(raceId)).thenReturn(Optional.of(ownedRace("owner@example.com")));
        when(raceStaffAssignmentRepository.existsActiveByRaceIdAndUserEmailAndStaffRole(
            raceId, "official@example.com", User.UserRole.OFFICIAL)).thenReturn(true);
        authenticate("official@example.com", "ROLE_OFFICIAL");

        RaceAccessGuard guard = new RaceAccessGuard(raceRepository, raceStaffAssignmentRepository);
        assertThatCode(() -> guard.assertCanPerformOperationalAction(raceId)).doesNotThrowAnyException();
    }

    @Test
    void checkpointVolunteerCannotManageRaceWithoutRaceLevelAssignment() {
        when(raceRepository.findByIdForAuthorization(raceId)).thenReturn(Optional.of(ownedRace("owner@example.com")));
        authenticate("volunteer@example.com", "ROLE_VOLUNTEER");

        RaceAccessGuard guard = new RaceAccessGuard(raceRepository, raceStaffAssignmentRepository);
        assertThatThrownBy(() -> guard.assertCanManage(raceId))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void checkpointVolunteerCanAccessOnlyExplicitlyAssignedCheckpoint() {
        UUID checkpointId = UUID.randomUUID();
        when(raceRepository.findByIdForAuthorization(raceId)).thenReturn(Optional.of(ownedRace("owner@example.com")));
        when(raceStaffAssignmentRepository.existsActiveByRaceIdAndUserEmailAndStaffRole(
            raceId, "volunteer@example.com", User.UserRole.VOLUNTEER)).thenReturn(false);
        when(raceStaffAssignmentRepository.existsCheckpointVolunteerByRaceIdAndCheckpointIdAndUserEmail(
            raceId, checkpointId, "volunteer@example.com")).thenReturn(true);
        authenticate("volunteer@example.com", "ROLE_VOLUNTEER");

        RaceAccessGuard guard = new RaceAccessGuard(raceRepository, raceStaffAssignmentRepository);
        assertThatCode(() -> guard.assertCanAccessCheckpoint(raceId, checkpointId)).doesNotThrowAnyException();
    }

    @Test
    void checkpointVolunteerCannotReadRaceWithoutRaceLevelAssignment() {
        when(raceRepository.findByIdForAuthorization(raceId)).thenReturn(Optional.of(ownedRace("owner@example.com")));
        when(raceStaffAssignmentRepository.existsActiveByRaceIdAndUserEmailAndStaffRole(
            raceId, "volunteer@example.com", User.UserRole.VOLUNTEER)).thenReturn(false);
        authenticate("volunteer@example.com", "ROLE_VOLUNTEER");

        RaceAccessGuard guard = new RaceAccessGuard(raceRepository, raceStaffAssignmentRepository);
        assertThatThrownBy(() -> guard.assertCanRead(raceId))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void checkpointOnlyVolunteerCannotPerformRaceLevelOperationalAction() {
        when(raceRepository.findByIdForAuthorization(raceId)).thenReturn(Optional.of(ownedRace("owner@example.com")));
        when(raceStaffAssignmentRepository.existsActiveByRaceIdAndUserEmailAndStaffRole(
            raceId, "volunteer@example.com", User.UserRole.VOLUNTEER)).thenReturn(false);
        authenticate("volunteer@example.com", "ROLE_VOLUNTEER");

        RaceAccessGuard guard = new RaceAccessGuard(raceRepository, raceStaffAssignmentRepository);
        assertThatThrownBy(() -> guard.assertCanPerformOperationalAction(raceId))
            .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void authenticatedNormalUserCanReadKnownRace() {
        when(raceRepository.findByIdForAuthorization(raceId)).thenReturn(Optional.of(publicRace("owner@example.com")));
        authenticate("athlete@example.com", "ROLE_ATHLETE");

        RaceAccessGuard guard = new RaceAccessGuard(raceRepository, raceStaffAssignmentRepository);
        assertThatCode(() -> guard.assertCanRead(raceId)).doesNotThrowAnyException();
    }

    @Test
    void athleteAndSpectatorCannotReadDraftRace() {
        Race race = ownedRace("owner@example.com");
        when(raceRepository.findByIdForAuthorization(raceId)).thenReturn(Optional.of(race));
        RaceAccessGuard guard = new RaceAccessGuard(raceRepository, raceStaffAssignmentRepository);

        authenticate("athlete@example.com", "ROLE_ATHLETE");
        assertThatThrownBy(() -> guard.assertCanRead(raceId)).isInstanceOf(ResourceNotFoundException.class);

        authenticate("spectator@example.com", "ROLE_SPECTATOR");
        assertThatThrownBy(() -> guard.assertCanRead(raceId)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void personalRolesCannotReadPrivateRace() {
        Race race = publicRace("owner@example.com");
        race.setIsPublicRegistration(false);
        when(raceRepository.findByIdForAuthorization(raceId)).thenReturn(Optional.of(race));
        RaceAccessGuard guard = new RaceAccessGuard(raceRepository, raceStaffAssignmentRepository);

        authenticate("athlete@example.com", "ROLE_ATHLETE");
        assertThatThrownBy(() -> guard.assertCanRead(raceId)).isInstanceOf(ResourceNotFoundException.class);

        authenticate("spectator@example.com", "ROLE_SPECTATOR");
        assertThatThrownBy(() -> guard.assertCanRead(raceId)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void personalRolesCannotReadArchivedRace() {
        Race race = publicRace("owner@example.com");
        race.setArchivedAt(OffsetDateTime.now());
        when(raceRepository.findByIdForAuthorization(raceId)).thenReturn(Optional.of(race));
        RaceAccessGuard guard = new RaceAccessGuard(raceRepository, raceStaffAssignmentRepository);

        authenticate("athlete@example.com", "ROLE_ATHLETE");
        assertThatThrownBy(() -> guard.assertCanRead(raceId)).isInstanceOf(ResourceNotFoundException.class);

        authenticate("spectator@example.com", "ROLE_SPECTATOR");
        assertThatThrownBy(() -> guard.assertCanRead(raceId)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void missingRaceRaisesNotFound() {
        when(raceRepository.findByIdForAuthorization(raceId)).thenReturn(Optional.empty());
        RaceAccessGuard guard = new RaceAccessGuard(raceRepository, raceStaffAssignmentRepository);

        assertThatThrownBy(() -> guard.assertCanManage(raceId))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void unauthenticatedCallerCannotManageRace() {
        when(raceRepository.findByIdForAuthorization(raceId))
            .thenReturn(Optional.of(ownedRace("owner@example.com")));

        RaceAccessGuard guard = new RaceAccessGuard(raceRepository, raceStaffAssignmentRepository);

        assertThatThrownBy(() -> guard.assertCanManage(raceId))
            .isInstanceOf(AccessDeniedException.class);
    }

    private Race publicRace(String ownerEmail) {
        Race race = ownedRace(ownerEmail);
        race.setStatus(Race.RaceStatus.REGISTRATION_OPEN);
        race.setIsPublicRegistration(true);
        return race;
    }
}
