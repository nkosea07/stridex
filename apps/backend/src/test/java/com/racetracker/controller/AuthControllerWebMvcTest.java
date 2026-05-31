package com.racetracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.racetracker.dto.auth.AuthResponse;
import com.racetracker.dto.auth.LoginRequest;
import com.racetracker.dto.auth.UserDto;
import com.racetracker.security.JwtAuthenticationFilter;
import com.racetracker.service.AuthService;
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
    private JwtAuthenticationFilter jwtAuthenticationFilter;

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
}
