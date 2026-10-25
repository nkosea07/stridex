package com.racetracker.controller;

import com.racetracker.config.CorsProperties;
import com.racetracker.config.RaceTrackerProperties;
import com.racetracker.config.SecurityConfig;
import com.racetracker.model.Race;
import com.racetracker.model.User;
import com.racetracker.repository.RaceRepository;
import com.racetracker.repository.RaceStaffAssignmentRepository;
import com.racetracker.security.ApiSecurityExceptionHandler;
import com.racetracker.security.CustomUserDetailsService;
import com.racetracker.security.JwtService;
import com.racetracker.security.RaceAccessGuard;
import com.racetracker.service.AuditEventService;
import com.racetracker.service.ParticipantService;
import com.racetracker.service.impl.MfaAuthenticationServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RaceParticipantController.class)
@Import({SecurityConfig.class, ApiSecurityExceptionHandler.class, RaceAccessGuard.class})
@EnableConfigurationProperties({RaceTrackerProperties.class, CorsProperties.class})
@TestPropertySource(properties = {
    "server.servlet.context-path=",
    "racetracker.api.version=1",
    "cors.allowed-origins=http://localhost:5173"
})
class RaceParticipantExportSecurityWebMvcTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private ParticipantService participantService;
    @MockBean private AuditEventService auditEventService;
    @MockBean private RaceRepository raceRepository;
    @MockBean private RaceStaffAssignmentRepository raceStaffAssignmentRepository;
    @MockBean private JwtService jwtService;
    @MockBean private CustomUserDetailsService customUserDetailsService;
    @MockBean private MfaAuthenticationServiceImpl mfaAuthenticationService;

    @Test
    void volunteerCannotExportParticipants() throws Exception {
        UUID raceId = UUID.randomUUID();

        mockMvc.perform(get("/v1/races/{raceId}/participants/export", raceId)
                .with(user("volunteer@example.com").roles("VOLUNTEER")))
            .andExpect(status().isForbidden());

        verifyNoInteractions(raceRepository, participantService, auditEventService);
    }

    @Test
    void raceAdminCannotExportAnotherOrganizersParticipants() throws Exception {
        UUID raceId = UUID.randomUUID();
        Race race = new Race();
        race.setId(raceId);
        race.setCreatedBy(User.builder().id(UUID.randomUUID()).email("owner@example.com").build());
        when(raceRepository.findByIdForAuthorization(raceId)).thenReturn(Optional.of(race));

        mockMvc.perform(get("/v1/races/{raceId}/participants/export", raceId)
                .with(user("other@example.com").roles("RACE_ADMIN")))
            .andExpect(status().isForbidden());

        verify(raceRepository).findByIdForAuthorization(raceId);
        verifyNoInteractions(participantService, auditEventService);
    }
}
