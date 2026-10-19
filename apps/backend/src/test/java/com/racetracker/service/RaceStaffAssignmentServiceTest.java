package com.racetracker.service;

import com.racetracker.dto.staff.RaceStaffAssignmentDto;
import com.racetracker.dto.staff.RaceStaffAssignmentRequest;
import com.racetracker.model.Race;
import com.racetracker.model.RaceStaffAssignment;
import com.racetracker.model.StaffRole;
import com.racetracker.model.User;
import com.racetracker.repository.RaceRepository;
import com.racetracker.repository.RaceStaffAssignmentRepository;
import com.racetracker.repository.UserRepository;
import com.racetracker.security.RaceAccessGuard;
import com.racetracker.service.impl.RaceStaffAssignmentServiceImpl;
import org.springframework.security.access.AccessDeniedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RaceStaffAssignmentServiceTest {

    @Mock UserRepository userRepository;
    @Mock RaceRepository raceRepository;
    @Mock RaceStaffAssignmentRepository assignmentRepository;
    @Mock AuditEventService auditEventService;
    @Mock RaceAccessGuard raceAccessGuard;

    private RaceStaffAssignmentServiceImpl service;
    private User actor;
    private User target;
    private Race race;

    @BeforeEach
    void setUp() {
        service = new RaceStaffAssignmentServiceImpl(
            userRepository, raceRepository, assignmentRepository, auditEventService, raceAccessGuard);
        actor = User.builder().id(UUID.randomUUID()).email("admin@example.com")
            .isActive(true).emailVerified(true).role(User.UserRole.SUPER_ADMIN).build();
        target = User.builder().id(UUID.randomUUID()).email("official@example.com")
            .isActive(true).emailVerified(true).role(User.UserRole.ATHLETE).build();
        race = Race.builder().id(UUID.randomUUID()).build();
    }

    @Test
    void superAdminPromotionChangesOnlyEligibleVerifiedUserRoleAndAudits() {
        when(userRepository.findByIdForUpdate(target.getId())).thenReturn(Optional.of(target));
        when(userRepository.findByEmailIgnoreCase(actor.getEmail())).thenReturn(Optional.of(actor));
        when(userRepository.save(target)).thenReturn(target);

        service.promote(target.getId(), StaffRole.OFFICIAL, actor.getEmail());

        assertThat(target.getRole()).isEqualTo(User.UserRole.OFFICIAL);
        verify(userRepository).findByIdForUpdate(target.getId());
        verify(userRepository).save(target);
        verify(auditEventService).recordInCurrentTransaction(
            "STAFF_ROLE_PROMOTED", actor.getId(), "USER", target.getId().toString(), "SUCCESS", 
            java.util.Map.of("role", "OFFICIAL"));
    }

    @Test
    void assignsAndRevokesStaffForOneRace() {
        target.setRole(User.UserRole.OFFICIAL);
        when(raceRepository.findByIdForUpdate(race.getId())).thenReturn(Optional.of(race));
        when(userRepository.findByEmailIgnoreCase(actor.getEmail())).thenReturn(Optional.of(actor));
        when(userRepository.findByIdForUpdate(target.getId())).thenReturn(Optional.of(target));
        when(assignmentRepository.findFirstByRaceIdAndUserIdAndActiveTrue(race.getId(), target.getId()))
            .thenReturn(Optional.empty());
        when(assignmentRepository.save(any(RaceStaffAssignment.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        RaceStaffAssignmentRequest request = new RaceStaffAssignmentRequest();
        request.setUserId(target.getId());
        request.setRole(StaffRole.OFFICIAL);

        RaceStaffAssignmentDto assigned = service.assign(race.getId(), request, actor.getEmail());

        assertThat(assigned.raceId()).isEqualTo(race.getId());
        assertThat(assigned.userId()).isEqualTo(target.getId());
        assertThat(assigned.role()).isEqualTo(User.UserRole.OFFICIAL);
        verify(userRepository).findByIdForUpdate(target.getId());

        RaceStaffAssignment existing = RaceStaffAssignment.builder()
            .id(UUID.randomUUID()).race(race).user(target).staffRole(User.UserRole.OFFICIAL).active(true).build();
        when(assignmentRepository.findFirstByRaceIdAndUserIdAndActiveTrue(race.getId(), target.getId()))
            .thenReturn(Optional.of(existing));

        service.revoke(race.getId(), target.getId(), actor.getEmail());

        assertThat(existing.getActive()).isFalse();
        assertThat(existing.getRevokedBy()).isSameAs(actor);
        verify(auditEventService).recordInCurrentTransaction(
            "RACE_STAFF_ASSIGNED", actor.getId(), "RACE", race.getId().toString(), "SUCCESS",
            java.util.Map.of("assignedUserId", target.getId().toString(), "staffRole", "OFFICIAL"));
        verify(auditEventService).recordInCurrentTransaction(
            "RACE_STAFF_REVOKED", actor.getId(), "RACE", race.getId().toString(), "SUCCESS",
            java.util.Map.of("revokedUserId", target.getId().toString(), "staffRole", "OFFICIAL"));
    }

    @Test
    void promotionIsBlockedWhenOtherStaffRoleHasActiveAssignments() {
        target.setRole(User.UserRole.OFFICIAL);
        when(userRepository.findByIdForUpdate(target.getId())).thenReturn(Optional.of(target));
        when(userRepository.findByEmailIgnoreCase(actor.getEmail())).thenReturn(Optional.of(actor));
        when(assignmentRepository.existsByUserIdAndStaffRoleAndActiveTrue(
            target.getId(), User.UserRole.OFFICIAL)).thenReturn(true);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            service.promote(target.getId(), StaffRole.VOLUNTEER, actor.getEmail()))
            .isInstanceOf(com.racetracker.exception.ConflictException.class)
            .hasFieldOrPropertyWithValue("code", "STAFF_ROLE_ASSIGNMENTS_CONFLICT");
        org.mockito.Mockito.verify(userRepository, org.mockito.Mockito.never()).save(target);
    }

    @Test
    void assignmentServiceEnforcesRaceOwnershipBeforePersisting() {
        User raceAdmin = User.builder().id(UUID.randomUUID()).email("owner@example.com")
            .isActive(true).emailVerified(true).role(User.UserRole.RACE_ADMIN).build();
        when(raceRepository.findByIdForUpdate(race.getId())).thenReturn(Optional.of(race));
        when(userRepository.findByEmailIgnoreCase(raceAdmin.getEmail())).thenReturn(Optional.of(raceAdmin));
        org.mockito.Mockito.doThrow(new AccessDeniedException("wrong race"))
            .when(raceAccessGuard).assertCanManage(race, raceAdmin);

        RaceStaffAssignmentRequest request = new RaceStaffAssignmentRequest();
        request.setUserId(target.getId());
        request.setRole(StaffRole.OFFICIAL);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            service.assign(race.getId(), request, raceAdmin.getEmail()))
            .isInstanceOf(AccessDeniedException.class);
        org.mockito.Mockito.verifyNoInteractions(assignmentRepository);
    }
}
