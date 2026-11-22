package com.racetracker.controller;

import com.racetracker.exception.GlobalExceptionHandler;
import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.security.CertificateTokenService;
import com.racetracker.security.PublicRequestLimiter;
import com.racetracker.service.CertificateService;
import com.racetracker.service.PublicRaceVisibility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PrivateRaceCertificateAccessTest {

    private final UUID raceId = UUID.randomUUID();
    private final UUID participantId = UUID.randomUUID();
    private final CertificateTokenService tokens =
        new CertificateTokenService("test-certificate-token-secret-value-1234567890");
    private final CertificateService certificateService = mock(CertificateService.class);
    private final PublicRaceVisibility visibility = mock(PublicRaceVisibility.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        doThrow(new ResourceNotFoundException("Race not found")).when(visibility).requireVisible(raceId);
        ResultController controller = new ResultController(null, null, mock(PublicRequestLimiter.class),
            null, certificateService, tokens, null, null, visibility, null, null);
        mvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new GlobalExceptionHandler())
            .addPlaceholderValue("racetracker.api.version", "1")
            .build();
    }

    private String certificatePath(UUID participant) {
        return "/v1/public/races/" + raceId + "/results/" + participant + "/certificate";
    }

    @Test
    void ownerTokenOpensTheCertificateOfAPrivateRace() throws Exception {
        when(certificateService.generate(raceId, participantId)).thenReturn(new byte[] {'%', 'P', 'D', 'F'});

        mvc.perform(get(certificatePath(participantId)).param("token", tokens.issue(raceId, participantId)))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_PDF));

        verify(visibility, never()).requireVisible(raceId);
    }

    @Test
    void privateRaceStaysHiddenWithoutAToken() throws Exception {
        mvc.perform(get(certificatePath(participantId))).andExpect(status().isNotFound());

        verifyNoInteractions(certificateService);
    }

    @Test
    void tokenForAnotherParticipantDoesNotUnlockAPrivateRace() throws Exception {
        String otherToken = tokens.issue(raceId, UUID.randomUUID());

        mvc.perform(get(certificatePath(participantId)).param("token", otherToken)).andExpect(status().isNotFound());

        verifyNoInteractions(certificateService);
    }

    @Test
    void tokenForAnotherRaceIsRejectedOnAPublicRace() throws Exception {
        UUID publicRace = UUID.randomUUID();
        String otherRaceToken = tokens.issue(UUID.randomUUID(), participantId);

        mvc.perform(get("/v1/public/races/" + publicRace + "/results/" + participantId + "/certificate")
                .param("token", otherRaceToken))
            .andExpect(status().isForbidden());

        verifyNoInteractions(certificateService);
    }
}
