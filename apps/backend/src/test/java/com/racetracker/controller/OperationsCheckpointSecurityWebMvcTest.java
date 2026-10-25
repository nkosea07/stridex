package com.racetracker.controller;

import com.racetracker.config.CorsProperties;
import com.racetracker.config.RaceTrackerProperties;
import com.racetracker.config.SecurityConfig;
import com.racetracker.dto.emergency.EmergencyAlertDto;
import com.racetracker.model.Race;
import com.racetracker.model.User;
import com.racetracker.repository.CheckpointRepository;
import com.racetracker.repository.RaceRepository;
import com.racetracker.repository.RaceStaffAssignmentRepository;
import com.racetracker.security.ApiSecurityExceptionHandler;
import com.racetracker.security.CustomUserDetailsService;
import com.racetracker.security.JwtService;
import com.racetracker.security.RaceAccessGuard;
import com.racetracker.service.AuditEventService;
import com.racetracker.service.CheckpointEmergencyService;
import com.racetracker.service.EmergencyAlertService;
import com.racetracker.service.OperationsCheckpointService;
import com.racetracker.service.impl.MfaAuthenticationServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {OperationsCheckpointController.class, EmergencyAlertController.class})
@Import({SecurityConfig.class, ApiSecurityExceptionHandler.class, RaceAccessGuard.class,
    CheckpointEmergencyService.class})
@EnableConfigurationProperties({RaceTrackerProperties.class, CorsProperties.class})
@TestPropertySource(properties = {"server.servlet.context-path=", "racetracker.api.version=1",
    "cors.allowed-origins=http://localhost:5173"})
class OperationsCheckpointSecurityWebMvcTest {
    @Autowired MockMvc mvc;
    @MockBean OperationsCheckpointService operations;
    @MockBean CheckpointRepository checkpoints;
    @MockBean RaceRepository races;
    @MockBean RaceStaffAssignmentRepository staff;
    @MockBean EmergencyAlertService alerts;
    @MockBean AuditEventService audit;
    @MockBean JwtService jwt;
    @MockBean CustomUserDetailsService users;
    @MockBean MfaAuthenticationServiceImpl mfa;

    @Test
    void volunteerOnlyDiscovery() throws Exception {
        when(operations.list("volunteer@example.com", 0, 50)).thenReturn(Page.empty());
        mvc.perform(get("/v1/operations/checkpoints").with(user("volunteer@example.com").roles("VOLUNTEER")))
            .andExpect(status().isOk());
        verify(operations).list("volunteer@example.com", 0, 50);
        mvc.perform(get("/v1/operations/checkpoints").with(user("athlete@example.com").roles("ATHLETE")))
            .andExpect(status().isForbidden());
        mvc.perform(get("/v1/operations/checkpoints")).andExpect(status().isUnauthorized());
    }

    @Test
    void checkpointSosAllowsAssignedVolunteerOfficialAndAdmin() throws Exception {
        UUID raceId = UUID.randomUUID();
        UUID checkpointId = UUID.randomUUID();
        when(checkpoints.existsByRaceIdAndId(raceId, checkpointId)).thenReturn(true);
        Race race = new Race();
        race.setId(raceId);
        race.setCreatedBy(User.builder().email("admin@example.com").build());
        when(races.findByIdForAuthorization(raceId)).thenReturn(Optional.of(race));
        when(staff.existsCheckpointVolunteerByRaceIdAndCheckpointIdAndUserEmail(raceId, checkpointId,
            "volunteer@example.com")).thenReturn(true);
        when(staff.existsActiveByRaceIdAndUserEmailAndStaffRole(raceId, "official@example.com",
            User.UserRole.OFFICIAL)).thenReturn(true);
        when(staff.existsActiveByRaceIdAndUserEmailAndStaffRole(raceId, "racevolunteer@example.com",
            User.UserRole.VOLUNTEER)).thenReturn(true);
        EmergencyAlertDto dto = new EmergencyAlertDto();
        dto.setId(UUID.randomUUID());
        when(alerts.raise(eq(raceId), any(), any())).thenReturn(dto);

        for (String[] actor : new String[][] {{"volunteer@example.com", "VOLUNTEER"},
            {"racevolunteer@example.com", "VOLUNTEER"}, {"official@example.com", "OFFICIAL"},
            {"admin@example.com", "RACE_ADMIN"},
            {"super@example.com", "SUPER_ADMIN"}}) {
            mvc.perform(post("/v1/races/{raceId}/checkpoints/{checkpointId}/emergency", raceId, checkpointId)
                    .with(user(actor[0]).roles(actor[1])).contentType("application/json").content("{}"))
                .andExpect(status().isAccepted());
        }
    }

    @Test
    void checkpointSosDeniesAnonymousAthleteAndUnassignedAdmin() throws Exception {
        UUID raceId = UUID.randomUUID();
        UUID checkpointId = UUID.randomUUID();
        when(checkpoints.existsByRaceIdAndId(raceId, checkpointId)).thenReturn(true);
        Race race = new Race();
        race.setId(raceId);
        race.setCreatedBy(User.builder().email("owner@example.com").build());
        when(races.findByIdForAuthorization(raceId)).thenReturn(Optional.of(race));

        mvc.perform(post("/v1/races/{raceId}/checkpoints/{checkpointId}/emergency", raceId, checkpointId)
                .contentType("application/json").content("{}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/v1/races/{raceId}/checkpoints/{checkpointId}/emergency", raceId, checkpointId)
                .with(user("athlete@example.com").roles("ATHLETE"))
                .contentType("application/json").content("{}"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/v1/races/{raceId}/checkpoints/{checkpointId}/emergency", raceId, checkpointId)
                .with(user("other@example.com").roles("RACE_ADMIN"))
                .contentType("application/json").content("{}"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/v1/races/{raceId}/checkpoints/{checkpointId}/emergency", raceId, checkpointId)
                .with(user("other@example.com").roles("OFFICIAL"))
                .contentType("application/json").content("{}"))
            .andExpect(status().isForbidden());
        verifyNoInteractions(alerts, audit);
    }
}
