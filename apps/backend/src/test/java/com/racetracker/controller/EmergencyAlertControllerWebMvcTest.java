package com.racetracker.controller;

import com.racetracker.dto.emergency.EmergencyAlertDto;
import com.racetracker.dto.emergency.EmergencyAlertRequest;
import com.racetracker.security.JwtAuthenticationFilter;
import com.racetracker.security.RaceAccessGuard;
import com.racetracker.service.AuditEventService;
import com.racetracker.service.EmergencyAlertService;
import com.racetracker.service.CheckpointEmergencyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = EmergencyAlertController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
    "server.servlet.context-path=",
    "racetracker.api.version=1"
})
class EmergencyAlertControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EmergencyAlertService emergencyAlertService;

    @MockBean
    private AuditEventService auditEventService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private RaceAccessGuard raceAccessGuard;

    @MockBean
    private CheckpointEmergencyService checkpointEmergencyService;

    @Test
    @WithMockUser(username = "volunteer@example.com", roles = "VOLUNTEER")
    void checkpointVolunteerRaisesDurableAlert() throws Exception {
        UUID raceId = UUID.randomUUID();
        UUID checkpointId = UUID.randomUUID();
        EmergencyAlertDto alert = new EmergencyAlertDto();
        alert.setId(UUID.randomUUID());
        alert.setDeliveryStatus("QUEUED");
        when(checkpointEmergencyService.raise(eq(raceId), eq(checkpointId),
            any(EmergencyAlertRequest.class), eq("volunteer@example.com"))).thenReturn(alert);

        mockMvc.perform(post("/v1/races/{raceId}/checkpoints/{checkpointId}/emergency", raceId, checkpointId)
                .principal(new UsernamePasswordAuthenticationToken("volunteer@example.com", null,
                    java.util.List.of(new SimpleGrantedAuthority("ROLE_VOLUNTEER"))))
                .contentType("application/json").content("{\"message\":\"Need assistance\"}"))
            .andExpect(status().isAccepted())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.deliveryStatus")
                .value("QUEUED"));

        verify(checkpointEmergencyService).raise(eq(raceId), eq(checkpointId),
            any(EmergencyAlertRequest.class), eq("volunteer@example.com"));
    }

    @Test
    @WithMockUser(username = "volunteer@example.com", roles = "VOLUNTEER")
    void mismatchedCheckpointDoesNotRaiseOrAudit() throws Exception {
        UUID raceId = UUID.randomUUID();
        UUID checkpointId = UUID.randomUUID();
        doThrow(new com.racetracker.exception.ResourceNotFoundException("Checkpoint not found"))
            .when(checkpointEmergencyService).raise(eq(raceId), eq(checkpointId),
                any(EmergencyAlertRequest.class), eq("volunteer@example.com"));
        mockMvc.perform(post("/v1/races/{raceId}/checkpoints/{checkpointId}/emergency", raceId, checkpointId)
                .principal(new UsernamePasswordAuthenticationToken("volunteer@example.com", null))
                .contentType("application/json").content("{}"))
            .andExpect(status().isNotFound());
        verifyNoInteractions(emergencyAlertService, auditEventService, raceAccessGuard);
    }

    @Test
    @WithMockUser(username = "volunteer@example.com", roles = "VOLUNTEER")
    void deniedCheckpointDoesNotRaiseOrAudit() throws Exception {
        UUID raceId = UUID.randomUUID();
        UUID checkpointId = UUID.randomUUID();
        doThrow(new AccessDeniedException("denied")).when(checkpointEmergencyService)
            .raise(eq(raceId), eq(checkpointId), any(EmergencyAlertRequest.class), eq("volunteer@example.com"));
        mockMvc.perform(post("/v1/races/{raceId}/checkpoints/{checkpointId}/emergency", raceId, checkpointId)
                .principal(new UsernamePasswordAuthenticationToken("volunteer@example.com", null))
                .contentType("application/json").content("{}"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(emergencyAlertService, auditEventService);
    }

    @Test
    void auditFailureDoesNotReturnAccepted() throws Exception {
        UUID raceId = UUID.randomUUID();
        UUID checkpointId = UUID.randomUUID();
        doThrow(new IllegalStateException("audit failed")).when(checkpointEmergencyService)
            .raise(eq(raceId), eq(checkpointId), any(EmergencyAlertRequest.class), eq("volunteer@example.com"));
        mockMvc.perform(post("/v1/races/{raceId}/checkpoints/{checkpointId}/emergency", raceId, checkpointId)
                .principal(new UsernamePasswordAuthenticationToken("volunteer@example.com", null))
                .contentType("application/json").content("{}"))
            .andExpect(status().is5xxServerError());
    }


    @Test
    @WithMockUser(username = "admin@example.com", roles = "RACE_ADMIN")
    void raceAdminCannotRaiseAlertForDeniedRace() throws Exception {
        UUID raceId = UUID.randomUUID();
        doThrow(new AccessDeniedException("denied"))
            .when(raceAccessGuard).assertCanManage(raceId);

        mockMvc.perform(post("/v1/races/{raceId}/emergency", raceId)
                .principal(new UsernamePasswordAuthenticationToken("admin@example.com", null,
                    java.util.List.of(new SimpleGrantedAuthority("ROLE_RACE_ADMIN"))))
                .contentType("application/json")
                .content("{\"latitude\":-17.8,\"longitude\":31.0,\"message\":\"Need assistance\"}"))
            .andExpect(status().isForbidden());

        verifyNoInteractions(emergencyAlertService, auditEventService);
    }

    @Test
    @WithMockUser(username = "athlete@example.com", authorities = {"ROLE_ATHLETE", "CAP_ATHLETE"})
    void athleteCanStillRaiseAlert() throws Exception {
        UUID raceId = UUID.randomUUID();
        EmergencyAlertRequest request = new EmergencyAlertRequest();
        request.setLatitude(new java.math.BigDecimal("-17.8"));
        request.setLongitude(new java.math.BigDecimal("31.0"));
        request.setMessage("Need assistance");
        EmergencyAlertDto alert = new EmergencyAlertDto();
        alert.setId(UUID.randomUUID());
        when(emergencyAlertService.raise(eq(raceId), eq(request), eq("athlete@example.com"))).thenReturn(alert);

        when(emergencyAlertService.raiseForAthlete(eq(raceId), any(EmergencyAlertRequest.class),
            eq("athlete@example.com"))).thenReturn(alert);

        mockMvc.perform(post("/v1/races/{raceId}/emergency", raceId)
                .principal(new UsernamePasswordAuthenticationToken("athlete@example.com", null,
                    java.util.List.of(new SimpleGrantedAuthority("CAP_ATHLETE"))))
                .contentType("application/json")
                .content("{\"latitude\":-17.8,\"longitude\":31.0,\"message\":\"Need assistance\"}"))
            .andExpect(status().isAccepted());

        verify(emergencyAlertService).raiseForAthlete(eq(raceId), any(EmergencyAlertRequest.class),
            eq("athlete@example.com"));
    }
}
