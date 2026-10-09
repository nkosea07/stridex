package com.racetracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.racetracker.dto.auth.AuthResponse;
import com.racetracker.dto.auth.LoginRequest;
import com.racetracker.dto.auth.RegisterOrganizerRequest;
import com.racetracker.dto.auth.UserDto;
import com.racetracker.security.JwtAuthenticationFilter;
import com.racetracker.security.ClientAddressResolver;
import com.racetracker.security.RequestRateLimiter;
import com.racetracker.service.AuthService;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
}
