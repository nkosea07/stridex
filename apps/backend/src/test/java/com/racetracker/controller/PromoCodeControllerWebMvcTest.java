package com.racetracker.controller;

import com.racetracker.security.JwtAuthenticationFilter;
import com.racetracker.security.RaceAccessGuard;
import com.racetracker.service.PromoCodeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PromoCodeController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
    "server.servlet.context-path=",
    "racetracker.api.version=1"
})
class PromoCodeControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PromoCodeService promoCodeService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private RaceAccessGuard raceAccessGuard;

    @Test
    void setActiveDeniesWhenRaceGuardRejectsRace() throws Exception {
        UUID raceId = UUID.randomUUID();
        UUID promoId = UUID.randomUUID();
        doThrow(new AccessDeniedException("denied"))
            .when(raceAccessGuard).assertCanManage(raceId);

        mockMvc.perform(patch("/v1/races/{raceId}/promo-codes/{promoId}/active", raceId, promoId)
                .contentType("application/json")
                .content("{\"active\":false}"))
            .andExpect(status().isForbidden());

        verifyNoInteractions(promoCodeService);
    }

    @Test
    void deleteDeniesWhenRaceGuardRejectsRace() throws Exception {
        UUID raceId = UUID.randomUUID();
        UUID promoId = UUID.randomUUID();
        doThrow(new AccessDeniedException("denied"))
            .when(raceAccessGuard).assertCanManage(raceId);

        mockMvc.perform(delete("/v1/races/{raceId}/promo-codes/{promoId}", raceId, promoId))
            .andExpect(status().isForbidden());

        verifyNoInteractions(promoCodeService);
    }
}
