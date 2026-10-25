package com.racetracker.service;

import com.racetracker.dto.emergency.EmergencyAlertDto;
import com.racetracker.dto.emergency.EmergencyAlertRequest;
import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.repository.CheckpointRepository;
import com.racetracker.security.RaceAccessGuard;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CheckpointEmergencyServiceTest {
    private final CheckpointRepository checkpoints = mock(CheckpointRepository.class);
    private final RaceAccessGuard guard = mock(RaceAccessGuard.class);
    private final EmergencyAlertService alerts = mock(EmergencyAlertService.class);
    private final AuditEventService audit = mock(AuditEventService.class);
    private final CheckpointEmergencyService service = new CheckpointEmergencyService(checkpoints, guard, alerts, audit);
    private final UUID raceId = UUID.randomUUID();
    private final UUID checkpointId = UUID.randomUUID();
    private final EmergencyAlertRequest request = new EmergencyAlertRequest();

    @Test
    void alertAndAuditUseOneTransactionBoundary() throws Exception {
        assertThat(CheckpointEmergencyService.class.getMethod("raise", UUID.class, UUID.class,
            EmergencyAlertRequest.class, String.class).getAnnotation(Transactional.class)).isNotNull();
        when(checkpoints.existsByRaceIdAndId(raceId, checkpointId)).thenReturn(true);
        EmergencyAlertDto alert = new EmergencyAlertDto();
        alert.setId(UUID.randomUUID());
        when(alerts.raise(raceId, request, "volunteer@example.com")).thenReturn(alert);
        assertThat(service.raise(raceId, checkpointId, request, "volunteer@example.com")).isSameAs(alert);
        var order = inOrder(checkpoints, guard, alerts, audit);
        order.verify(checkpoints).existsByRaceIdAndId(raceId, checkpointId);
        order.verify(guard).assertCanAccessCheckpoint(raceId, checkpointId);
        order.verify(alerts).raise(raceId, request, "volunteer@example.com");
        order.verify(audit).recordForActor(eq("EMERGENCY_ALERT_RAISED"), eq("volunteer@example.com"),
            eq("RACE"), eq(raceId.toString()), eq("SUCCESS"), any());
    }

    @Test
    void unknownCheckpointStopsBeforeGuardAndWrites() {
        assertThatThrownBy(() -> service.raise(raceId, checkpointId, request, "volunteer@example.com"))
            .isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(guard, alerts, audit);
    }

    @Test
    void deniedCheckpointStopsBeforeWrites() {
        when(checkpoints.existsByRaceIdAndId(raceId, checkpointId)).thenReturn(true);
        doThrow(new AccessDeniedException("denied")).when(guard).assertCanAccessCheckpoint(raceId, checkpointId);
        assertThatThrownBy(() -> service.raise(raceId, checkpointId, request, "volunteer@example.com"))
            .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(alerts, audit);
    }

    @Test
    void auditFailurePropagatesForTransactionRollback() {
        when(checkpoints.existsByRaceIdAndId(raceId, checkpointId)).thenReturn(true);
        EmergencyAlertDto alert = new EmergencyAlertDto();
        alert.setId(UUID.randomUUID());
        when(alerts.raise(raceId, request, "volunteer@example.com")).thenReturn(alert);
        doThrow(new IllegalStateException("audit failed")).when(audit).recordForActor(any(), any(), any(), any(), any(), any());
        assertThatThrownBy(() -> service.raise(raceId, checkpointId, request, "volunteer@example.com"))
            .isInstanceOf(IllegalStateException.class);
    }
}
