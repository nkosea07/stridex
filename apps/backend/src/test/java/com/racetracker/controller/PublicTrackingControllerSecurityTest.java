package com.racetracker.controller;

import com.racetracker.config.CorsProperties;
import com.racetracker.config.RaceTrackerProperties;
import com.racetracker.config.SecurityConfig;
import com.racetracker.dto.publictracking.PublicRaceHubResponse;
import com.racetracker.security.CustomUserDetailsService;
import com.racetracker.security.JwtService;
import com.racetracker.service.PublicTrackingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PublicTrackingController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties({RaceTrackerProperties.class, CorsProperties.class})
@TestPropertySource(properties = {
    "server.servlet.context-path=",
    "racetracker.api.version=1",
    "cors.allowed-origins=http://localhost:5173"
})
class PublicTrackingControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PublicTrackingService publicTrackingService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void getHub_allowsAnonymousAccessWhenFiltersAreEnabled() throws Exception {
        UUID raceId = UUID.randomUUID();

        PublicRaceHubResponse response = new PublicRaceHubResponse();
        response.setRaceId(raceId);
        response.setRaceName("City Marathon");
        response.setStatus("IN_PROGRESS");

        when(publicTrackingService.getRaceHub(raceId)).thenReturn(response);

        mockMvc.perform(get("/v1/public/races/{raceId}/hub", raceId))
            .andExpect(status().isOk());
    }

    @Test
    void liveTrackingRoute_stillRejectsAnonymousAccessWhenFiltersAreEnabled() throws Exception {
        mockMvc.perform(get("/v1/races/{raceId}/tracking/live", UUID.randomUUID()))
            .andExpect(status().isForbidden());
    }
}
