package com.racetracker.controller;

import com.racetracker.dto.race.RaceDto;
import com.racetracker.security.JwtAuthenticationFilter;
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

import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

    @Test
    void list_returnsRaces() throws Exception {
        RaceDto race = new RaceDto();
        race.setId(UUID.randomUUID());
        race.setName("City Marathon");
        race.setStatus("REGISTRATION_OPEN");
        race.setRaceDate(LocalDate.of(2026, 10, 10));

        when(raceService.list(eq(null), eq(null), any(Pageable.class)))
            .thenReturn(new PageImpl<>(java.util.List.of(race), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/v1/races"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].name").value("City Marathon"))
            .andExpect(jsonPath("$.content[0].status").value("REGISTRATION_OPEN"));
    }
}
