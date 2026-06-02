package com.racetracker.controller;

import com.racetracker.config.CorsProperties;
import com.racetracker.config.RaceTrackerProperties;
import com.racetracker.config.SecurityConfig;
import com.racetracker.dto.rfid.RfidReadEventBatchResponse;
import com.racetracker.exception.UnauthorizedException;
import com.racetracker.security.CustomUserDetailsService;
import com.racetracker.security.JwtService;
import com.racetracker.service.RfidIngestService;
import com.racetracker.service.RfidReaderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RfidController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties({RaceTrackerProperties.class, CorsProperties.class})
@TestPropertySource(properties = {
    "server.servlet.context-path=",
    "racetracker.api.version=1",
    "cors.allowed-origins=http://localhost:5173"
})
class RfidControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RfidReaderService rfidReaderService;

    @MockBean
    private RfidIngestService rfidIngestService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void readerAdminRoutesRejectAnonymousAccess() throws Exception {
        mockMvc.perform(get("/v1/races/{raceId}/rfid/readers", UUID.randomUUID()))
            .andExpect(status().isForbidden());
    }

    @Test
    void ingestRejectsMissingReaderKey() throws Exception {
        when(rfidIngestService.ingest(isNull(), any(), anyString()))
            .thenThrow(new UnauthorizedException("RFID reader API key is required"));

        mockMvc.perform(post("/v1/rfid/read-events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBatchJson()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("RFID reader API key is required"));
    }

    @Test
    void ingestRejectsInvalidReaderKey() throws Exception {
        when(rfidIngestService.ingest(eq("bad-key"), any(), anyString()))
            .thenThrow(new UnauthorizedException("Invalid RFID reader API key"));

        mockMvc.perform(post("/v1/rfid/read-events")
                .header("X-RFID-Reader-Key", "bad-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBatchJson()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Invalid RFID reader API key"));
    }

    @Test
    void ingestAcceptsValidReaderKey() throws Exception {
        RfidReadEventBatchResponse response = new RfidReadEventBatchResponse();
        response.setAccepted(1);
        when(rfidIngestService.ingest(eq("valid-key"), any(), anyString())).thenReturn(response);

        mockMvc.perform(post("/v1/rfid/read-events")
                .header("X-RFID-Reader-Key", "valid-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBatchJson()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accepted").value(1));
    }

    private String validBatchJson() {
        return """
            {
              "reads": [
                {
                  "sourceEventId": "event-1",
                  "rfidTag": "TAG-1",
                  "readAt": "2026-06-02T06:00:00Z"
                }
              ]
            }
            """;
    }
}
