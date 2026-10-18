package com.racetracker.controller;

import com.racetracker.config.CorsProperties;
import com.racetracker.config.RaceTrackerProperties;
import com.racetracker.config.SecurityConfig;
import com.racetracker.dto.rfid.RfidReadEventBatchResponse;
import com.racetracker.dto.rfid.RfidReaderDto;
import com.racetracker.dto.rfid.RfidRaceOverviewDto;
import com.racetracker.exception.TooManyRequestsException;
import com.racetracker.exception.UnauthorizedException;
import com.racetracker.security.ClientAddressResolver;
import com.racetracker.security.CustomUserDetailsService;
import com.racetracker.security.JwtService;
import com.racetracker.security.ApiSecurityExceptionHandler;
import com.racetracker.security.RaceAccessGuard;
import com.racetracker.security.RequestRateLimiter;
import com.racetracker.service.impl.MfaAuthenticationServiceImpl;
import com.racetracker.service.RfidIngestService;
import com.racetracker.service.RfidReaderService;
import com.racetracker.service.AuditEventService;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RfidController.class)
@Import({SecurityConfig.class, ApiSecurityExceptionHandler.class})
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
    private AuditEventService auditEventService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private MfaAuthenticationServiceImpl mfaAuthenticationService;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @MockBean
    private RaceAccessGuard raceAccessGuard;

    @MockBean
    private RequestRateLimiter requestRateLimiter;

    @MockBean
    private ClientAddressResolver clientAddressResolver;

    @BeforeEach
    void setUp() {
        when(clientAddressResolver.resolve(any())).thenReturn("127.0.0.1");
    }

    @Test
    void readerAdminRoutesRejectAnonymousAccess() throws Exception {
        mockMvc.perform(get("/v1/races/{raceId}/rfid/readers", UUID.randomUUID()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void ingestRejectsMissingReaderKey() throws Exception {
        when(rfidIngestService.ingest(isNull(), any(), anyString()))
            .thenThrow(new UnauthorizedException("RFID reader API key is required"));

        mockMvc.perform(post("/v1/rfid/read-events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBatchJson()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("Authentication failed."));
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
            .andExpect(jsonPath("$.message").value("Authentication failed."));
    }

    @Test
    void ingestAcceptsValidReaderKey() throws Exception {
        RfidReadEventBatchResponse response = new RfidReadEventBatchResponse();
        response.setAccepted(1);
        when(rfidIngestService.ingest(eq("valid-key"), any(), anyString())).thenReturn(response);
        when(clientAddressResolver.resolve(any())).thenReturn("203.0.113.20");

        mockMvc.perform(post("/v1/rfid/read-events")
                .header("X-RFID-Reader-Key", "valid-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBatchJson()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accepted").value(1));

        verify(requestRateLimiter).checkRfid("valid-key", "203.0.113.20");
    }

    @Test
    void ingestStopsBeforeServiceWhenRateLimited() throws Exception {
        when(clientAddressResolver.resolve(any())).thenReturn("203.0.113.20");
        doThrow(new TooManyRequestsException("Too many requests", 42))
            .when(requestRateLimiter).checkRfid("valid-key", "203.0.113.20");

        mockMvc.perform(post("/v1/rfid/read-events")
                .header("X-RFID-Reader-Key", "valid-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validBatchJson()))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.message").value("Too many requests. Please try again later."));

        verifyNoInteractions(rfidIngestService);
    }

    @Test
    void heartbeatAcceptsValidReaderKey() throws Exception {
        RfidReaderDto readerDto = new RfidReaderDto();
        readerDto.setGatewayStatus("ONLINE");
        when(rfidReaderService.heartbeat(eq("valid-key"), any(), anyString())).thenReturn(readerDto);

        mockMvc.perform(post("/v1/rfid/readers/heartbeat")
                .header("X-RFID-Reader-Key", "valid-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "gatewayName": "edge-1",
                      "gatewayBacklog": 12
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.gatewayStatus").value("ONLINE"));

        verifyNoInteractions(requestRateLimiter);
    }

    @Test
    void overviewReturnsReaderMetricsForAuthorizedUsers() throws Exception {
        RfidRaceOverviewDto overview = new RfidRaceOverviewDto();
        overview.setTotalReaders(2);
        overview.setQueueDepth(4);
        when(rfidReaderService.overview(any())).thenReturn(overview);

        mockMvc.perform(get("/v1/races/{raceId}/rfid/overview", UUID.randomUUID())
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("official")
                    .roles("OFFICIAL")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalReaders").value(2))
            .andExpect(jsonPath("$.queueDepth").value(4));
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
