package com.racetracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.racetracker.dto.auth.AuthResponse;
import com.racetracker.dto.auth.LoginRequest;
import com.racetracker.dto.auth.RegisterOrganizerRequest;
import com.racetracker.dto.auth.RegisterRequest;
import com.racetracker.dto.auth.UserDto;
import com.racetracker.model.UserCapabilityType;
import com.racetracker.security.JwtAuthenticationFilter;
import com.racetracker.security.ClientAddressResolver;
import com.racetracker.security.RequestRateLimiter;
import com.racetracker.service.AuthService;
import com.racetracker.service.AccountDeletionService;
import com.racetracker.service.OrganizerApplicationService;
import com.racetracker.service.impl.MfaAuthenticationServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "server.servlet.context-path=")
class AuthControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private AccountDeletionService accountDeletionService;

    @MockBean
    private OrganizerApplicationService organizerApplicationService;

    @MockBean
    private MfaAuthenticationServiceImpl mfaAuthenticationService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private RequestRateLimiter requestRateLimiter;

    @MockBean
    private ClientAddressResolver clientAddressResolver;

    @Test
    void login_returnsTokenAndUser() throws Exception {
        UserDto user = new UserDto();
        user.setEmail("athlete@example.com");
        user.setFirstName("Athlete");
        user.setLastName("Runner");

        AuthResponse response = AuthResponse.builder()
            .token("access-token")
            .refreshToken("refresh-token")
            .user(user)
            .build();

        when(authService.login(any(LoginRequest.class))).thenReturn(response);

        LoginRequest request = new LoginRequest();
        request.setEmail("athlete@example.com");
        request.setPassword("password123");

        mockMvc.perform(post("/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").value("access-token"))
            .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
            .andExpect(jsonPath("$.user.email").value("athlete@example.com"));
    }

    @Test
    void signedOutDeletionCancellationReturnsGenericMessageWithoutSession() throws Exception {
        mockMvc.perform(post("/v1/auth/deletion/cancel")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"athlete@example.com\",\"password\":\"secret123\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").exists())
            .andExpect(jsonPath("$.token").doesNotExist());

        verify(accountDeletionService).cancelWithPassword("athlete@example.com", "secret123");
    }

    @Test
    void organizerRegistration_usesPendingApplicationBoundary() throws Exception {
        when(organizerApplicationService.register(any(RegisterOrganizerRequest.class)))
            .thenReturn(AuthResponse.builder().requiresEmailVerification(true).user(new UserDto()).build());

        mockMvc.perform(post("/v1/auth/register-organizer")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "organizer@example.com",
                      "password": "password123",
                      "firstName": "Race",
                      "lastName": "Organizer",
                      "organizationName": "City Run Club"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.requiresEmailVerification").value(true));
    }

    @Test
    void spectatorRegistrationBindsPluralExperiences() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
            .thenReturn(AuthResponse.builder().requiresEmailVerification(true).user(new UserDto()).build());

        mockMvc.perform(post("/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "spectator@example.com",
                      "password": "password123",
                      "firstName": "Race",
                      "lastName": "Follower",
                      "experiences": ["SPECTATOR"]
                    }
                    """))
            .andExpect(status().isOk());

        var captor = org.mockito.ArgumentCaptor.forClass(RegisterRequest.class);
        verify(authService).register(captor.capture());
        assertThat(captor.getValue().getExperiences())
            .containsExactly(UserCapabilityType.SPECTATOR);
    }

    @Test
    void dualCapabilityRegistrationBindsBothExperiences() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
            .thenReturn(AuthResponse.builder().requiresEmailVerification(true).user(new UserDto()).build());

        mockMvc.perform(post("/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "dual@example.com",
                      "password": "password123",
                      "firstName": "Dual",
                      "lastName": "Runner",
                      "experiences": ["ATHLETE", "SPECTATOR"]
                    }
                    """))
            .andExpect(status().isOk());

        var captor = org.mockito.ArgumentCaptor.forClass(RegisterRequest.class);
        verify(authService).register(captor.capture());
        assertThat(captor.getValue().getExperiences())
            .containsExactlyInAnyOrder(UserCapabilityType.ATHLETE, UserCapabilityType.SPECTATOR);
    }

    @Test
    void capabilityProfileUpdateReturnsUpdatedCapabilities() throws Exception {
        UserDto user = new UserDto();
        user.setCapabilities(java.util.Set.of(com.racetracker.model.UserCapabilityType.SPECTATOR));
        user.setPreferredCapability(com.racetracker.model.UserCapabilityType.SPECTATOR);
        when(authService.updateCapabilities(any())).thenReturn(user);

        mockMvc.perform(put("/v1/auth/profile/capabilities")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"capabilities\":[\"SPECTATOR\"],\"preferredCapability\":\"SPECTATOR\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.capabilities[0]").value("SPECTATOR"))
            .andExpect(jsonPath("$.preferredCapability").value("SPECTATOR"));
    }

    @Test
    void malformedCapabilityRegistrationReturnsStableBadRequest() throws Exception {
        mockMvc.perform(post("/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "runner@example.com",
                      "password": "password123",
                      "firstName": "Race",
                      "lastName": "Runner",
                      "experience": "ORGANIZER"
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_CAPABILITY"));
    }
}
