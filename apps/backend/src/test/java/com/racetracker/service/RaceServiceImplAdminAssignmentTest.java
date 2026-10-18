package com.racetracker.service;

import com.racetracker.exception.ApiException;
import com.racetracker.exception.ConflictException;
import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.mapper.RaceMapper;
import com.racetracker.model.Race;
import com.racetracker.model.User;
import com.racetracker.repository.RaceRegistrationFieldRepository;
import com.racetracker.repository.RaceRepository;
import com.racetracker.repository.UserRepository;
import com.racetracker.security.RaceAccessGuard;
import com.racetracker.service.impl.RaceServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.HashSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RaceServiceImplAdminAssignmentTest {

    @Mock RaceRepository raceRepository;
    @Mock RaceMapper raceMapper;
    @Mock RaceRegistrationFieldRepository registrationFieldRepository;
    @Mock UserRepository userRepository;
    @Mock RaceCompletionService raceCompletionService;
    @Mock RaceAccessGuard raceAccessGuard;
    @Mock AuditEventService auditEventService;

    private RaceServiceImpl service;
    private final UUID raceId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new RaceServiceImpl(raceRepository, raceMapper, registrationFieldRepository, userRepository,
            raceCompletionService, raceAccessGuard, auditEventService);
    }

    @Test
    void assignsRaceAdminToLegacyUnownedRaceAndAudits() {
        Race race = Race.builder().id(raceId).build();
        User target = User.builder().id(userId).role(User.UserRole.RACE_ADMIN).build();
        when(raceRepository.findByIdForUpdate(raceId)).thenReturn(Optional.of(race));
        when(userRepository.findById(userId)).thenReturn(Optional.of(target));

        service.assignAdmin(raceId, userId, "root@example.com");

        assertThat(race.getAdmins()).containsExactly(target);
        verify(auditEventService).recordForActor(
            eq("RACE_ADMIN_ASSIGNED"), eq("root@example.com"), eq("RACE"), eq(raceId.toString()),
            eq("SUCCESS"), any());
    }

    @Test
    void rejectsDuplicateAssignment() {
        User target = User.builder().id(userId).role(User.UserRole.RACE_ADMIN).build();
        Race race = Race.builder().id(raceId).build();
        race.setAdmins(new HashSet<>());
        race.getAdmins().add(target);
        when(raceRepository.findByIdForUpdate(raceId)).thenReturn(Optional.of(race));
        when(userRepository.findById(userId)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.assignAdmin(raceId, userId, "root@example.com"))
            .isInstanceOf(ConflictException.class);
        verify(auditEventService, never()).recordForActor(any(), any(), any(), any(), any(), any());
    }

    @Test
    void rejectsNonRaceAdminTarget() {
        Race race = Race.builder().id(raceId).build();
        User target = User.builder().id(userId).role(User.UserRole.ATHLETE).build();
        when(raceRepository.findByIdForUpdate(raceId)).thenReturn(Optional.of(race));
        when(userRepository.findById(userId)).thenReturn(Optional.of(target));

        assertThatThrownBy(() -> service.assignAdmin(raceId, userId, "root@example.com"))
            .isInstanceOf(ApiException.class)
            .hasMessage("Target user must have the RACE_ADMIN role");
        assertThat(race.getAdmins()).isEmpty();
    }

    @Test
    void rejectsMissingRaceOrUser() {
        when(raceRepository.findByIdForUpdate(raceId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.assignAdmin(raceId, userId, "root@example.com"))
            .isInstanceOf(ResourceNotFoundException.class);

        Race race = Race.builder().id(raceId).build();
        when(raceRepository.findByIdForUpdate(raceId)).thenReturn(Optional.of(race));
        when(userRepository.findById(userId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.assignAdmin(raceId, userId, "root@example.com"))
            .isInstanceOf(ResourceNotFoundException.class);
    }
}
