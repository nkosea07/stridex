package com.racetracker.service;

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
import static org.mockito.Mockito.when;

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

    private void authenticate(String name, String authority) {
        var authentication = new UsernamePasswordAuthenticationToken(
            name,
            "password",
            List.of(new SimpleGrantedAuthority(authority))
        );
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
