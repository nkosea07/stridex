package com.racetracker.controller;

import com.racetracker.security.JwtAuthenticationFilter;
import com.racetracker.security.RaceAccessGuard;
import com.racetracker.service.CheckpointExportService;
import com.racetracker.service.CheckpointService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CheckpointController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
    "server.servlet.context-path=",
    "racetracker.api.version=1"
})
class CheckpointControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CheckpointService checkpointService;

    @MockBean
    private CheckpointExportService checkpointExportService;

    @MockBean
    private RaceAccessGuard raceAccessGuard;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    void staffMutationUsesManagerGuardAndDoesNotReachServiceWhenDenied() throws Exception {
        UUID raceId = UUID.randomUUID();
        doThrow(new AccessDeniedException("denied"))
            .when(raceAccessGuard).assertCanManage(raceId);

        mockMvc.perform(post("/v1/races/{raceId}/checkpoints", raceId)
                .contentType("application/json")
                .content("{\"name\":\"Water\",\"checkpointType\":\"WATER_STATION\",\"orderIndex\":1}"))
            .andExpect(status().isForbidden());

        verifyNoInteractions(checkpointService);
    }

    @Test
    void operationalRecordReadUsesCheckpointScopedGuard() throws Exception {
        UUID raceId = UUID.randomUUID();
        UUID checkpointId = UUID.randomUUID();
        org.mockito.Mockito.when(checkpointService.listRecords(raceId, checkpointId)).thenReturn(List.of());

        mockMvc.perform(get("/v1/races/{raceId}/checkpoints/{checkpointId}/records", raceId, checkpointId))
            .andExpect(status().isOk());

        verify(raceAccessGuard).assertCanAccessCheckpoint(raceId, checkpointId);
    }
}
