package com.racetracker.controller;

import com.racetracker.config.RaceTrackerProperties;
import com.racetracker.dto.auth.AuthResponse;
import com.racetracker.exception.TooManyRequestsException;
import com.racetracker.security.ClientAddressResolver;
import com.racetracker.security.JwtAuthenticationFilter;
import com.racetracker.security.RequestRateLimiter;
import com.racetracker.service.OAuthAccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.junit.jupiter.api.BeforeEach;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = OAuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "server.servlet.context-path=")
class OAuthControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OAuthAccountService oAuthAccountService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private RequestRateLimiter requestRateLimiter;

    @MockBean
    private ClientAddressResolver clientAddressResolver;

    @MockBean
    private RaceTrackerProperties raceTrackerProperties;

    @BeforeEach
    void enableOAuthForControllerTests() {
        RaceTrackerProperties.OAuth oauth = new RaceTrackerProperties.OAuth();
        oauth.setEnabled(true);
        when(raceTrackerProperties.getOauth()).thenReturn(oauth);
    }

    @Test
    void googleUsesResolvedClientAddressForRateLimiting() throws Exception {
        when(clientAddressResolver.resolve(any())).thenReturn("203.0.113.20");
        when(oAuthAccountService.authenticateWithGoogle("google-id-token"))
            .thenReturn(AuthResponse.builder().token("access-token").build());

        mockMvc.perform(post("/v1/auth/oauth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "idToken": "google-id-token",
                      "provider": "google"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").value("access-token"));

        verify(requestRateLimiter).checkOAuth(eq("203.0.113.20"));
    }

    @Test
    void googleStopsBeforeTokenExchangeWhenRateLimited() throws Exception {
        when(clientAddressResolver.resolve(any())).thenReturn("203.0.113.20");
        doThrow(new TooManyRequestsException("Too many OAuth requests", 42))
            .when(requestRateLimiter).checkOAuth("203.0.113.20");

        mockMvc.perform(post("/v1/auth/oauth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "idToken": "google-id-token",
                      "provider": "google"
                    }
                    """))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.message").value("Too many requests. Please try again later."));

        verifyNoInteractions(oAuthAccountService);
    }

    @Test
    void googleRejectsRequestsWhenOAuthIsDisabled() throws Exception {
        RaceTrackerProperties.OAuth oauth = new RaceTrackerProperties.OAuth();
        oauth.setEnabled(false);
        when(raceTrackerProperties.getOauth()).thenReturn(oauth);

        mockMvc.perform(post("/v1/auth/oauth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "idToken": "google-id-token",
                      "provider": "google"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("The request could not be completed."))
            .andExpect(jsonPath("$.code").value("API_ERROR"));

        verifyNoInteractions(requestRateLimiter, oAuthAccountService, clientAddressResolver);
    }
}
