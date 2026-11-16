package com.racetracker.controller;

import com.racetracker.config.CorsProperties;
import com.racetracker.config.RaceTrackerProperties;
import com.racetracker.config.SecurityConfig;
import com.racetracker.exception.ApiException;
import com.racetracker.exception.TooManyRequestsException;
import com.racetracker.model.User;
import com.racetracker.repository.UserRepository;
import com.racetracker.security.ClientAddressResolver;
import com.racetracker.security.CustomUserDetailsService;
import com.racetracker.security.RequestRateLimiter;
import com.racetracker.security.JwtService;
import com.racetracker.security.ApiSecurityExceptionHandler;
import com.racetracker.service.AccountDeletionService;
import com.racetracker.service.AccountPrivacyService;
import com.racetracker.service.impl.MfaAuthenticationServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AccountPrivacyController.class)
@Import({SecurityConfig.class, com.racetracker.security.AdminMfaPolicy.class, ApiSecurityExceptionHandler.class})
@EnableConfigurationProperties({RaceTrackerProperties.class, CorsProperties.class})
@TestPropertySource(properties = {
    "server.servlet.context-path=",
    "racetracker.api.version=1",
    "cors.allowed-origins=http://localhost:5173"
})
class AccountPrivacyControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccountPrivacyService accountPrivacyService;

    @MockBean
    private AccountDeletionService accountDeletionService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private MfaAuthenticationServiceImpl mfaAuthenticationService;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @MockBean
    private RequestRateLimiter requestRateLimiter;

    @MockBean
    private ClientAddressResolver clientAddressResolver;

    @Test
    void anonymousAccessIsDenied() throws Exception {
        mockMvc.perform(get("/v1/me/data")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/v1/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedUserCanExportData() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userRepository.findByEmailIgnoreCase("runner@example.com"))
            .thenReturn(Optional.of(User.builder().id(userId).email("runner@example.com").build()));
        when(accountPrivacyService.exportData(any())).thenReturn(Map.of("profile", Map.of()));

        mockMvc.perform(get("/v1/me/data").with(user("runner@example.com")))
            .andExpect(status().isOk());
    }

    @Test
    void wrongDeletionPasswordIsABadRequestNotAnExpiredSession() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userRepository.findByEmailIgnoreCase("runner@example.com"))
            .thenReturn(Optional.of(User.builder().id(userId).email("runner@example.com").build()));
        when(clientAddressResolver.resolve(any())).thenReturn("203.0.113.9");
        when(accountDeletionService.request(eq(userId), any()))
            .thenThrow(new ApiException("Password is incorrect", "ACCOUNT_DELETION_PASSWORD_INVALID"));

        mockMvc.perform(delete("/v1/me").with(user("runner@example.com"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"wrong\",\"confirmation\":\"DELETE\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("ACCOUNT_DELETION_PASSWORD_INVALID"));
        verify(requestRateLimiter).checkAccountDeletion(userId.toString(), "203.0.113.9");
    }

    @Test
    void rateLimitedDeletionNeverChecksThePassword() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userRepository.findByEmailIgnoreCase("runner@example.com"))
            .thenReturn(Optional.of(User.builder().id(userId).email("runner@example.com").build()));
        when(clientAddressResolver.resolve(any())).thenReturn("203.0.113.9");
        doThrow(new TooManyRequestsException("limited", 60))
            .when(requestRateLimiter).checkAccountDeletion(anyString(), anyString());

        mockMvc.perform(delete("/v1/me").with(user("runner@example.com"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"guess\",\"confirmation\":\"DELETE\"}"))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
        verify(accountDeletionService, never()).request(any(), any());
    }
}
