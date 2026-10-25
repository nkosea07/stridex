package com.racetracker.controller;

import com.racetracker.exception.GlobalExceptionHandler;
import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.security.CertificateTokenService;
import com.racetracker.security.ClientAddressResolver;
import com.racetracker.security.RaceAccessGuard;
import com.racetracker.security.RequestRateLimiter;
import com.racetracker.service.AuditEventService;
import com.racetracker.service.CertificateService;
import com.racetracker.service.MediaService;
import com.racetracker.service.ParticipantService;
import com.racetracker.service.PublicRaceVisibility;
import com.racetracker.service.PublicTrackingService;
import com.racetracker.service.RaceRouteService;
import com.racetracker.service.RankingService;
import com.racetracker.service.ResultExportService;
import com.racetracker.service.ResultService;
import com.racetracker.dto.route.RoutePointDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PublicRaceVisibilityControllerTest {

    private final UUID raceId = UUID.randomUUID();
    private final UUID mediaId = UUID.randomUUID();
    private final UUID participantId = UUID.randomUUID();
    private final PublicRaceVisibility visibility = mock(PublicRaceVisibility.class);
    private final RaceRouteService routeService = mock(RaceRouteService.class);
    private final MediaService mediaService = mock(MediaService.class);
    private final ParticipantService participantService = mock(ParticipantService.class);
    private final ResultService resultService = mock(ResultService.class);
    private final CertificateService certificateService = mock(CertificateService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        RaceAccessGuard accessGuard = mock(RaceAccessGuard.class);
        AuditEventService auditEvents = mock(AuditEventService.class);
        RequestRateLimiter rateLimiter = mock(RequestRateLimiter.class);
        ClientAddressResolver addressResolver = mock(ClientAddressResolver.class);
        mvc = MockMvcBuilders.standaloneSetup(
                new RaceRouteController(routeService, accessGuard, visibility),
                new MediaController(mediaService, auditEvents, accessGuard, visibility),
                new PublicTrackingController(mock(PublicTrackingService.class), participantService,
                    visibility, rateLimiter, addressResolver),
                new ResultController(resultService, auditEvents, rateLimiter, addressResolver,
                    mock(ResultExportService.class), certificateService, mock(CertificateTokenService.class),
                    mock(RankingService.class), accessGuard, visibility)
            )
            .setControllerAdvice(new GlobalExceptionHandler())
            .addPlaceholderValue("racetracker.api.version", "1")
            .build();
    }

    @Test
    void allOtherPublicRaceFeedsReturn404BeforeReadingHiddenRaceData() throws Exception {
        doThrow(new ResourceNotFoundException("Race not found")).when(visibility).requireVisible(raceId);
        String base = "/v1/public/races/" + raceId;
        for (String path : List.of(
            "/registration-fields", "/route", "/media",
            "/media/" + mediaId + "/content", "/media/" + mediaId + "/thumbnail",
            "/results", "/results/" + participantId + "/certificate"
        )) {
            mvc.perform(get(base + path))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        }
        verifyNoInteractions(routeService, mediaService, participantService, resultService, certificateService);
    }

    @Test
    void visiblePublicRouteReturnsRoutePoints() throws Exception {
        RoutePointDto point = new RoutePointDto(0, new BigDecimal("-33.9"), new BigDecimal("18.4"), null);
        when(routeService.getRoute(raceId)).thenReturn(List.of(point));

        mvc.perform(get("/v1/public/races/{raceId}/route", raceId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].sequence").value(0));

        verify(visibility).requireVisible(raceId);
        verify(routeService).getRoute(raceId);
    }
}
