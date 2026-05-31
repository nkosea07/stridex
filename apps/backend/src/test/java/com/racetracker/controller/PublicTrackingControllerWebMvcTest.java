package com.racetracker.controller;

import com.racetracker.dto.publictracking.PublicAthleteTrackingResponse;
import com.racetracker.dto.publictracking.PublicRaceAthleteSummaryResponse;
import com.racetracker.dto.publictracking.PublicRaceHubResponse;
import com.racetracker.dto.tracking.CheckpointSplitDto;
import com.racetracker.security.JwtAuthenticationFilter;
import com.racetracker.service.PublicTrackingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PublicTrackingController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "server.servlet.context-path=")
class PublicTrackingControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PublicTrackingService publicTrackingService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void getHub_allowsAnonymousAccess() throws Exception {
        UUID raceId = UUID.randomUUID();

        PublicRaceAthleteSummaryResponse athlete = new PublicRaceAthleteSummaryResponse();
        athlete.setParticipantId(UUID.randomUUID());
        athlete.setParticipantName("Taylor Swiftfoot");
        athlete.setBibNumber("A-102");
        athlete.setStatus("STARTED");

        PublicRaceHubResponse response = new PublicRaceHubResponse();
        response.setRaceId(raceId);
        response.setRaceName("City Marathon");
        response.setStatus("IN_PROGRESS");
        response.setLeaderboard(List.of(athlete));

        when(publicTrackingService.getRaceHub(raceId)).thenReturn(response);

        mockMvc.perform(get("/v1/public/races/{raceId}/hub", raceId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.raceId").value(raceId.toString()))
            .andExpect(jsonPath("$.leaderboard[0].participantName").value("Taylor Swiftfoot"));
    }

    @Test
    void getAthleteTracking_returnsOnlyPublicFields() throws Exception {
        UUID raceId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();

        PublicAthleteTrackingResponse response = new PublicAthleteTrackingResponse();
        response.setParticipantId(participantId);
        response.setParticipantName("Taylor Swiftfoot");
        response.setBibNumber("A-102");
        response.setStatus("STARTED");
        response.setLastCheckpoint("10K");
        response.setElapsedTime("01:12:00");
        response.setAveragePace("04:48");
        response.setPredictedFinishTime("2026-05-31T10:15:00Z");
        response.setDistanceRemaining(new BigDecimal("5.75"));
        response.setSplits(List.of(new CheckpointSplitDto("10K", "00:48:00")));

        when(publicTrackingService.getAthleteTracking(raceId, participantId)).thenReturn(response);

        mockMvc.perform(get("/v1/public/races/{raceId}/tracking/athletes/{participantId}", raceId, participantId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.participantId").value(participantId.toString()))
            .andExpect(jsonPath("$.participantName").value("Taylor Swiftfoot"))
            .andExpect(jsonPath("$.bibNumber").value("A-102"))
            .andExpect(jsonPath("$.lastCheckpoint").value("10K"))
            .andExpect(jsonPath("$.splits[0].name").value("10K"))
            .andExpect(jsonPath("$.email").doesNotExist())
            .andExpect(jsonPath("$.phone").doesNotExist())
            .andExpect(jsonPath("$.medicalConditions").doesNotExist())
            .andExpect(jsonPath("$.deviceToken").doesNotExist());
    }
}
