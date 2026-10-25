package com.racetracker.service;

import com.racetracker.dto.checkpoint.CheckpointRecordRequest;
import com.racetracker.service.impl.TimingImportServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TimingImportServiceImplTest {

    @Test
    void importsValidTimingRowsAndReportsRowErrors() {
        CheckpointService checkpointService = mock(CheckpointService.class);
        when(checkpointService.recordImported(any(), any(), any())).thenReturn(null);
        UUID raceId = UUID.randomUUID();
        UUID checkpointId = UUID.randomUUID();
        String csv = "checkpointId,participantBibNumber,recordedAt,method\n"
            + checkpointId + ",101,2026-10-25T08:00:00Z,MANUAL\n"
            + "not-a-uuid,102,2026-10-25T08:01:00Z,MANUAL\n";
        MockMultipartFile file = new MockMultipartFile("file", "timing.csv", "text/csv",
            csv.getBytes(StandardCharsets.UTF_8));

        var result = new TimingImportServiceImpl(checkpointService).importCsv(raceId, file);

        assertThat(result.totalRows()).isEqualTo(2);
        assertThat(result.imported()).isEqualTo(1);
        assertThat(result.errors()).hasSize(1);
        verify(checkpointService).recordImported(org.mockito.ArgumentMatchers.eq(raceId),
            org.mockito.ArgumentMatchers.eq(checkpointId), any(CheckpointRecordRequest.class));
    }

    @Test
    void revokedAssignmentAbortsImportInsteadOfReportingARecoverableRowError() {
        CheckpointService checkpointService = mock(CheckpointService.class);
        UUID raceId = UUID.randomUUID();
        UUID checkpointId = UUID.randomUUID();
        String csv = "checkpointId,participantBibNumber,recordedAt\n"
            + checkpointId + ",101,2026-10-25T08:00:00Z\n";
        MockMultipartFile file = new MockMultipartFile("file", "timing.csv", "text/csv",
            csv.getBytes(StandardCharsets.UTF_8));
        org.mockito.Mockito.doThrow(new AccessDeniedException("assignment revoked"))
            .when(checkpointService).recordImported(any(), any(), any());

        assertThatThrownBy(() -> new TimingImportServiceImpl(checkpointService).importCsv(raceId, file))
            .isInstanceOf(AccessDeniedException.class);
    }
}
