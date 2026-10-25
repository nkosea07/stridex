package com.racetracker.controller;

import com.racetracker.security.JwtAuthenticationFilter;
import com.racetracker.service.OperationsCheckpointService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = OperationsCheckpointController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {"server.servlet.context-path=", "racetracker.api.version=1"})
class OperationsCheckpointControllerWebMvcTest {
    @Autowired MockMvc mockMvc;
    @MockBean OperationsCheckpointService service;
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    @WithMockUser(username = "volunteer@example.com", roles = "VOLUNTEER")
    void listsOnlyForPrincipalWithDefaults() throws Exception {
        when(service.list("volunteer@example.com", 0, 50)).thenReturn(Page.empty());
        mockMvc.perform(get("/v1/operations/checkpoints")
                .principal(new UsernamePasswordAuthenticationToken("volunteer@example.com", null)))
            .andExpect(status().isOk());
        verify(service).list("volunteer@example.com", 0, 50);
    }

    @Test
    @WithMockUser(roles = "VOLUNTEER")
    void rejectsOversizedPage() throws Exception {
        mockMvc.perform(get("/v1/operations/checkpoints?size=101"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

}
