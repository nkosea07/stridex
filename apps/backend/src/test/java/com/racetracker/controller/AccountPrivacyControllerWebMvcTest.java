package com.racetracker.controller;

import com.racetracker.config.CorsProperties;
import com.racetracker.config.RaceTrackerProperties;
import com.racetracker.config.SecurityConfig;
import com.racetracker.model.User;
import com.racetracker.repository.UserRepository;
import com.racetracker.security.CustomUserDetailsService;
import com.racetracker.security.JwtService;
import com.racetracker.service.AccountPrivacyService;
import com.racetracker.service.impl.MfaAuthenticationServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AccountPrivacyController.class)
@Import(SecurityConfig.class)
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
    private UserRepository userRepository;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private MfaAuthenticationServiceImpl mfaAuthenticationService;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void anonymousAccessIsDenied() throws Exception {
        mockMvc.perform(get("/v1/me/data")).andExpect(status().isForbidden());
        mockMvc.perform(delete("/v1/me")).andExpect(status().isForbidden());
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
}
