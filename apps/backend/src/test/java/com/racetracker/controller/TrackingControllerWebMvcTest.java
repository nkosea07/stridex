package com.racetracker.controller;

import com.racetracker.dto.tracking.LiveTrackingResponse;
import com.racetracker.security.JwtAuthenticationFilter;
import com.racetracker.security.RaceAccessGuard;
import com.racetracker.service.TrackingService;
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

@WebMvcTest(controllers = TrackingController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "server.servlet.context-path=")
class TrackingControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TrackingService trackingService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private RaceAccessGuard raceAccessGuard;

    @Test
    void getLiveData_returnsCurrentPositions() throws Exception {
        UUID raceId = UUID.randomUUID();

        LiveTrackingResponse row = new LiveTrackingResponse();
        row.setParticipantId(UUID.randomUUID());
        row.setBibNumber("A-102");
        row.setParticipantName("Taylor Swiftfoot");
        row.setLatitude(new BigDecimal("-33.92490"));
        row.setLongitude(new BigDecimal("18.42410"));
        row.setStatus("STARTED");

        when(trackingService.getLiveData(raceId)).thenReturn(List.of(row));

        mockMvc.perform(get("/v1/races/{raceId}/tracking/live", raceId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].bibNumber").value("A-102"))
            .andExpect(jsonPath("$[0].participantName").value("Taylor Swiftfoot"));
    }
}
