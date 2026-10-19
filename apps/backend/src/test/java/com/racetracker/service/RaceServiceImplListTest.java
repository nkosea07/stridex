package com.racetracker.service;

import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.mapper.RaceMapper;
import com.racetracker.model.Race;
import com.racetracker.model.User;
import com.racetracker.repository.RaceRegistrationFieldRepository;
import com.racetracker.repository.RaceRepository;
import com.racetracker.repository.UserRepository;
import com.racetracker.security.RaceAccessGuard;
import com.racetracker.service.impl.RaceServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(MockitoExtension.class)
class RaceServiceImplListTest {

    @Mock RaceRepository raceRepository;
    @Mock RaceMapper raceMapper;
    @Mock RaceRegistrationFieldRepository registrationFieldRepository;
    @Mock UserRepository userRepository;
    @Mock RaceCompletionService raceCompletionService;
    @Mock RaceAccessGuard raceAccessGuard;
    @Mock AuditEventService auditEventService;

    private RaceServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RaceServiceImpl(raceRepository, raceMapper, registrationFieldRepository, userRepository,
            raceCompletionService, raceAccessGuard, auditEventService);
    }

    @AfterEach
    void clearAuthentication() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    void usesTypedGlobalRepositoryMethodWhenStatusIsAbsent() {
        authenticate("root@example.com", "ROLE_SUPER_ADMIN");
        when(raceRepository.findAllByArchivedAtIsNull(any())).thenReturn(new PageImpl<>(List.of()));

        service.list(null, false, false, PageRequest.of(0, 20));

        verify(raceRepository).findAllByArchivedAtIsNull(any());
        verify(raceRepository, never()).searchForOwner(any(), anyBoolean(), anyBoolean(), any(), any());
    }

    @Test
    void usesOwnerScopedQueryForRaceAdminWithoutNullableStatusBinding() {
        UUID ownerId = UUID.randomUUID();
        User owner = User.builder().id(ownerId).email("admin@example.com").build();
        authenticate("admin@example.com", "ROLE_RACE_ADMIN");
        when(userRepository.findByEmailIgnoreCase("admin@example.com")).thenReturn(Optional.of(owner));
        when(raceRepository.searchForOwner(eq(ownerId), eq(false), eq(false), any(), any()))
            .thenReturn(new PageImpl<>(List.of()));

        service.list(null, false, false, PageRequest.of(0, 20));

        verify(raceRepository).searchForOwner(eq(ownerId), eq(false), eq(false), any(), any());
        verify(raceRepository, never()).searchForOwnerByStatus(any(), any(), anyBoolean(), anyBoolean(), any(), any());
    }

    @Test
    void bindsStatusOnlyWhenStatusIsPresent() {
        authenticate("root@example.com", "ROLE_SUPER_ADMIN");
        when(raceRepository.findByStatusAndArchivedAtIsNull(eq(Race.RaceStatus.DRAFT), any()))
            .thenReturn(new PageImpl<>(List.of()));

        service.list("DRAFT", false, false, PageRequest.of(0, 20));

        verify(raceRepository).findByStatusAndArchivedAtIsNull(eq(Race.RaceStatus.DRAFT), any());
    }

    @Test
    void usesOwnerScopedStatusQueryWhenRaceAdminFiltersByStatus() {
        UUID ownerId = UUID.randomUUID();
        User owner = User.builder().id(ownerId).email("admin@example.com").build();
        authenticate("admin@example.com", "ROLE_RACE_ADMIN");
        when(userRepository.findByEmailIgnoreCase("admin@example.com")).thenReturn(Optional.of(owner));
        when(raceRepository.searchForOwnerByStatus(eq(ownerId), eq(Race.RaceStatus.DRAFT), eq(true), eq(true), any(), any()))
            .thenReturn(new PageImpl<>(List.of()));

        service.list("DRAFT", true, true, PageRequest.of(0, 20));

        verify(raceRepository).searchForOwnerByStatus(
            eq(ownerId), eq(Race.RaceStatus.DRAFT), eq(true), eq(true), any(), any());
    }

    @Test
    void officialListingUsesOnlyActiveOfficialAssignments() {
        UUID userId = UUID.randomUUID();
        User official = User.builder().id(userId).email("official@example.com").build();
        authenticate("official@example.com", "ROLE_OFFICIAL");
        when(userRepository.findByEmailIgnoreCase("official@example.com")).thenReturn(Optional.of(official));
        when(raceRepository.searchForStaff(eq(userId), eq(User.UserRole.OFFICIAL), eq(false), eq(false), any(), any()))
            .thenReturn(new PageImpl<>(List.of()));

        service.list(null, false, false, PageRequest.of(0, 20));

        verify(raceRepository).searchForStaff(
            eq(userId), eq(User.UserRole.OFFICIAL), eq(false), eq(false), any(), any());
        verify(raceRepository, never()).findAllByArchivedAtIsNull(any());
    }

    @Test
    void volunteerListingUsesOnlyActiveRaceAssignments() {
        UUID userId = UUID.randomUUID();
        User volunteer = User.builder().id(userId).email("volunteer@example.com").build();
        authenticate("volunteer@example.com", "ROLE_VOLUNTEER");
        when(userRepository.findByEmailIgnoreCase("volunteer@example.com")).thenReturn(Optional.of(volunteer));
        when(raceRepository.searchForStaffByStatus(
            eq(userId), eq(User.UserRole.VOLUNTEER), eq(Race.RaceStatus.IN_PROGRESS), eq(true), eq(false), any(), any()))
            .thenReturn(new PageImpl<>(List.of()));

        service.list("IN_PROGRESS", true, false, PageRequest.of(0, 20));

        verify(raceRepository).searchForStaffByStatus(
            eq(userId), eq(User.UserRole.VOLUNTEER), eq(Race.RaceStatus.IN_PROGRESS), eq(true), eq(false), any(), any());
        verify(raceRepository, never()).findByStatusAndArchivedAtIsNull(any(), any());
    }

    @Test
    void athleteListingUsesPublicNonDraftNonArchivedQueryEvenWhenIncludeArchivedIsRequested() {
        authenticate("athlete@example.com", "ROLE_ATHLETE");
        when(raceRepository.searchForPublic(eq(Race.RaceStatus.DRAFT), eq(false), any(), any()))
            .thenReturn(new PageImpl<>(List.of()));

        service.list(null, false, true, PageRequest.of(0, 20));

        verify(raceRepository).searchForPublic(eq(Race.RaceStatus.DRAFT), eq(false), any(), any());
        verify(raceRepository, never()).findAll(any(org.springframework.data.domain.Pageable.class));
        verify(raceRepository, never()).findAllByArchivedAtIsNull(any());
        verifyNoInteractions(userRepository);
    }

    @Test
    void spectatorStatusListingUsesPublicQueryAndCannotRequestDraftsOrArchives() {
        authenticate("spectator@example.com", "ROLE_SPECTATOR");
        when(raceRepository.searchForPublicByStatus(
            eq(Race.RaceStatus.DRAFT), eq(Race.RaceStatus.REGISTRATION_OPEN), eq(false), any(), any()))
            .thenReturn(new PageImpl<>(List.of()));

        service.list("REGISTRATION_OPEN", false, true, PageRequest.of(0, 20));

        verify(raceRepository).searchForPublicByStatus(
            eq(Race.RaceStatus.DRAFT), eq(Race.RaceStatus.REGISTRATION_OPEN), eq(false), any(), any());
        verify(raceRepository, never()).findByStatus(any(), any());
        verifyNoInteractions(userRepository);
    }

    @Test
    void personalRoleCannotGetDraftRaceDetail() {
        UUID raceId = UUID.randomUUID();
        Race race = new Race();
        race.setId(raceId);
        race.setStatus(Race.RaceStatus.DRAFT);
        authenticate("athlete@example.com", "ROLE_ATHLETE");
        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));

        assertThatThrownBy(() -> service.getById(raceId))
            .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(raceMapper, registrationFieldRepository);
    }

    private void authenticate(String name, String authority) {
        var authentication = new UsernamePasswordAuthenticationToken(
            name,
            "password",
            List.of(new SimpleGrantedAuthority(authority))
        );
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
