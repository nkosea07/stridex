package com.racetracker.controller;

import com.racetracker.dto.race.RaceDto;
import com.racetracker.security.JwtAuthenticationFilter;
import com.racetracker.security.RaceAccessGuard;
import com.racetracker.service.RaceService;
import com.racetracker.service.AuditEventService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RaceController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "server.servlet.context-path=")
class RaceControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RaceService raceService;

    @MockBean
    private AuditEventService auditEventService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private RaceAccessGuard raceAccessGuard;

    @Test
    void list_returnsRaces() throws Exception {
        RaceDto race = new RaceDto();
        race.setId(UUID.randomUUID());
        race.setName("City Marathon");
        race.setStatus("REGISTRATION_OPEN");
        race.setRaceDate(LocalDate.of(2026, 10, 10));

        when(raceService.list(eq(null), eq(null), eq(false), any(Pageable.class)))
            .thenReturn(new PageImpl<>(java.util.List.of(race), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/v1/races"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].name").value("City Marathon"))
            .andExpect(jsonPath("$.content[0].status").value("REGISTRATION_OPEN"));
    }

    @Test
    @WithMockUser(username = "root@example.com", roles = "SUPER_ADMIN")
    void assignAdminDelegatesToServiceAndReturnsMessage() throws Exception {
        UUID raceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        mockMvc.perform(post("/v1/races/{raceId}/admins", raceId)
                .with(user("root@example.com").roles("SUPER_ADMIN"))
                .principal(new UsernamePasswordAuthenticationToken(
                    "root@example.com",
                    "n/a",
                    java.util.List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))
                ))
                .contentType("application/json")
                .content("{\"userId\":\"" + userId + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Race admin assigned"));

        verify(raceService).assignAdmin(eq(raceId), eq(userId), anyString());
    }

    @Test
    @WithMockUser(username = "root@example.com", roles = "SUPER_ADMIN")
    void assignAdminRequiresUserId() throws Exception {
        mockMvc.perform(post("/v1/races/{raceId}/admins", UUID.randomUUID())
                .with(user("root@example.com").roles("SUPER_ADMIN"))
                .contentType("application/json")
                .content("{}"))
            .andExpect(status().isBadRequest());
    }
}
