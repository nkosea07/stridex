package com.racetracker.controller;

import com.racetracker.security.JwtAuthenticationFilter;
import com.racetracker.security.RaceAccessGuard;
import com.racetracker.service.BibService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = BibController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
    "server.servlet.context-path=",
    "racetracker.api.version=1"
})
class BibControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BibService bibService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private RaceAccessGuard raceAccessGuard;

    @Test
    void getBib_returnsPdf() throws Exception {
        UUID raceId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        byte[] mockPdf = "fake-pdf-content".getBytes();

        when(bibService.generateBibPdf(eq(raceId), eq(participantId))).thenReturn(mockPdf);

        mockMvc.perform(get("/v1/races/{raceId}/bibs/{participantId}", raceId, participantId))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_PDF))
            .andExpect(header().string("Content-Disposition", "attachment; filename=bib-" + participantId + ".pdf"))
            .andExpect(content().bytes(mockPdf));
    }

    @Test
    void getBib_deniesWhenRaceGuardRejectsRace() throws Exception {
        UUID raceId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        doThrow(new AccessDeniedException("denied"))
            .when(raceAccessGuard).assertCanReadStaff(raceId);

        mockMvc.perform(get("/v1/races/{raceId}/bibs/{participantId}", raceId, participantId))
            .andExpect(status().isForbidden());

        verifyNoInteractions(bibService);
    }
}
