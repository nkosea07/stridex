package com.racetracker.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.racetracker.dto.staff.RaceStaffAssignmentDto;
import com.racetracker.model.StaffRole;
import com.racetracker.model.User;
import com.racetracker.security.JwtAuthenticationFilter;
import com.racetracker.security.RaceAccessGuard;
import com.racetracker.service.RaceStaffAssignmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RaceStaffAssignmentController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
    "server.servlet.context-path=",
    "racetracker.api.version=1"
})
class RaceStaffAssignmentControllerWebMvcTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean RaceStaffAssignmentService assignmentService;
    @MockBean RaceAccessGuard raceAccessGuard;
    @MockBean JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    @WithMockUser(username = "admin@example.com", roles = "RACE_ADMIN")
    void assignUsesAuthenticatedActorAndRaceGuard() throws Exception {
        UUID raceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        RaceStaffAssignmentDto dto = new RaceStaffAssignmentDto(
            UUID.randomUUID(), raceId, userId, User.UserRole.OFFICIAL, true,
            OffsetDateTime.now(), null);
        when(assignmentService.assign(eq(raceId), any(), eq("admin@example.com"))).thenReturn(dto);
        mockMvc.perform(post("/v1/races/{raceId}/staff", raceId)
                .with(user("admin@example.com").roles("RACE_ADMIN"))
                .principal(new UsernamePasswordAuthenticationToken(
                    "admin@example.com", "n/a", List.of(new SimpleGrantedAuthority("ROLE_RACE_ADMIN"))))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of(
                    "userId", userId,
                    "role", StaffRole.OFFICIAL.name()))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.raceId").value(raceId.toString()))
            .andExpect(jsonPath("$.userId").value(userId.toString()))
            .andExpect(jsonPath("$.role").value("OFFICIAL"));

        verify(raceAccessGuard).assertCanManage(raceId);
        verify(assignmentService).assign(eq(raceId), any(), eq("admin@example.com"));
    }
}
