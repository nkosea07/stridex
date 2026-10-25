package com.racetracker.controller;

import com.racetracker.security.RaceAccessGuard;
import com.racetracker.exception.GlobalExceptionHandler;
import com.racetracker.exception.ServiceUnavailableException;
import com.racetracker.exception.ApiException;
import com.racetracker.service.AuditEventService;
import com.racetracker.service.ExportedFile;
import com.racetracker.service.ParticipantService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RaceParticipantExportControllerTest {
    private final UUID raceId = UUID.randomUUID();
    private final ParticipantService service = mock(ParticipantService.class);
    private final AuditEventService audit = mock(AuditEventService.class);
    private final RaceAccessGuard guard = mock(RaceAccessGuard.class);
    private final RaceParticipantController controller = new RaceParticipantController(service, audit, guard);
    private final UsernamePasswordAuthenticationToken actor =
        new UsernamePasswordAuthenticationToken("admin@example.com", null);

    @Test
    void omittedFormatDefaultsToCsv() throws Exception {
        byte[] bytes = "id,firstName\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(service.export(raceId, "csv")).thenReturn(new ExportedFile(bytes, "text/csv", "participants.csv"));

        var response = MockMvcBuilders.standaloneSetup(controller)
            .addPlaceholderValue("racetracker.api.version", "1").build()
            .perform(get("/v1/races/{raceId}/participants/export", raceId).principal(actor))
            .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsByteArray()).isEqualTo(bytes);
        verify(service).export(raceId, "csv");
    }

    @Test
    void defaultsToCsvAndReturnsFileMetadata() {
        byte[] bytes = "id,firstName\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(service.export(raceId, "csv")).thenReturn(new ExportedFile(bytes, "text/csv", "participants.csv"));

        var response = controller.exportCsv(raceId, "csv", actor);

        assertThat(response.getBody()).isSameAs(bytes);
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("text/csv");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
            .isEqualTo("attachment; filename=\"participants.csv\"");
        verify(guard).assertCanManage(raceId);
        verify(audit).recordForActor("PARTICIPANT_DATA_EXPORTED", "admin@example.com", "RACE",
            raceId.toString(), "SUCCESS", Map.of("format", "csv"));
    }

    @Test
    void xlsxUsesWorkbookMetadataAndAuditsActualFormat() {
        byte[] bytes = new byte[]{'P', 'K', 3, 4};
        String type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        when(service.export(raceId, "xlsx")).thenReturn(new ExportedFile(bytes, type, "participants.xlsx"));

        var response = controller.exportCsv(raceId, " XLSX ", actor);

        assertThat(response.getBody()).isSameAs(bytes);
        assertThat(response.getHeaders().getContentType().toString()).isEqualTo(type);
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
            .isEqualTo("attachment; filename=\"participants.xlsx\"");
        verify(audit).recordForActor("PARTICIPANT_DATA_EXPORTED", "admin@example.com", "RACE",
            raceId.toString(), "SUCCESS", Map.of("format", "xlsx"));
    }

    @Test
    void deniedRaceNeverExportsOrAudits() {
        doThrow(new AccessDeniedException("denied")).when(guard).assertCanManage(raceId);

        assertThatThrownBy(() -> controller.exportCsv(raceId, "xlsx", actor))
            .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(service, audit);
    }

    @Test
    void exportGenerationFailureReturnsSafe503WithStableCode() throws Exception {
        when(service.export(raceId, "xlsx"))
            .thenThrow(new ServiceUnavailableException("Internal workbook I/O details", "EXPORT_GENERATION_FAILED"));

        MockMvcBuilders.standaloneSetup(controller)
            .addPlaceholderValue("racetracker.api.version", "1")
            .setControllerAdvice(new GlobalExceptionHandler()).build()
            .perform(get("/v1/races/{raceId}/participants/export", raceId)
                .param("format", "xlsx").principal(actor))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("EXPORT_GENERATION_FAILED"))
            .andExpect(jsonPath("$.message").value(
                "The service is temporarily unavailable. Please try again shortly."));
        verifyNoInteractions(audit);
    }

    @Test
    void oversizedExportReturnsStable400Code() throws Exception {
        when(service.export(raceId, "csv"))
            .thenThrow(new ApiException("Participant export exceeds pilot limit", "EXPORT_TOO_LARGE"));

        MockMvcBuilders.standaloneSetup(controller)
            .addPlaceholderValue("racetracker.api.version", "1")
            .setControllerAdvice(new GlobalExceptionHandler()).build()
            .perform(get("/v1/races/{raceId}/participants/export", raceId).principal(actor))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("EXPORT_TOO_LARGE"));
        verifyNoInteractions(audit);
    }
}
