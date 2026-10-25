package com.racetracker.service;

import com.racetracker.config.RaceTrackerProperties;
import com.racetracker.exception.ApiException;
import com.racetracker.exception.ServiceUnavailableException;
import com.racetracker.mapper.ImportJobMapper;
import com.racetracker.mapper.ParticipantMapper;
import com.racetracker.model.Participant;
import com.racetracker.repository.*;
import com.racetracker.security.CapabilityAuthorizationService;
import com.racetracker.security.RaceAccessGuard;
import com.racetracker.service.impl.ParticipantIdentityService;
import com.racetracker.service.impl.ParticipantImportProcessor;
import com.racetracker.service.impl.ParticipantServiceImpl;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Collections;
import java.util.AbstractList;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;

class ParticipantExportServiceTest {
    private final UUID raceId = UUID.randomUUID();
    private final ParticipantRepository participants = mock(ParticipantRepository.class);
    private final RaceAccessGuard guard = mock(RaceAccessGuard.class);
    private final ParticipantServiceImpl service = new ParticipantServiceImpl(
        participants, mock(RaceRepository.class), mock(RaceCategoryRepository.class), mock(UserRepository.class),
        mock(ImportJobRepository.class), mock(ParticipantMapper.class), mock(ImportJobMapper.class),
        new RaceTrackerProperties(), mock(ParticipantCredentialService.class), mock(TeamRepository.class),
        mock(RaceRegistrationFieldRepository.class), mock(ParticipantRegistrationAnswerRepository.class),
        mock(RegistrationEmailService.class), mock(NotificationService.class), mock(ParticipantImportProcessor.class),
        mock(CapabilityAuthorizationService.class), guard, mock(ParticipantIdentityService.class));

    @Test
    void csvKeepsV1HeaderAndEscaping() {
        ParticipantExportRow participant = participant();
        when(participants.findExportRowsByRaceId(eq(raceId), any(Pageable.class))).thenReturn(List.of(participant));

        ExportedFile file = service.export(raceId, null);

        assertThat(file.contentType()).isEqualTo("text/csv");
        assertThat(file.filename()).isEqualTo("participants-" + raceId + ".csv");
        assertThat(new String(file.content(), StandardCharsets.UTF_8)).isEqualTo(
            "id,firstName,lastName,email,phone,bibNumber,status,gender,nationality,registeredAt\n"
                + "\"" + participant.id() + "\",\"A, \"\"Runner\"\"\",\"Doe\",\"a@example.com\",\"123\",\"B7\",\"REGISTERED\",,\"ZW\",\"2026-01-02T03:04Z\"\n");
        verify(guard).assertCanManage(raceId);
    }

    @Test
    void xlsxContainsMatchingColumnsAndRealWorkbook() throws Exception {
        ParticipantExportRow participant = participant();
        when(participants.findExportRowsByRaceId(eq(raceId), any(Pageable.class))).thenReturn(List.of(participant));

        ExportedFile file = service.export(raceId, "xlsx");

        assertThat(file.contentType()).isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        assertThat(file.filename()).isEqualTo("participants-" + raceId + ".xlsx");
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(file.content()))) {
            var sheet = workbook.getSheet("Participants");
            assertThat(sheet).isNotNull();
            assertThat(sheet.getPhysicalNumberOfRows()).isEqualTo(2);
            assertThat(sheet.getRow(0).getPhysicalNumberOfCells()).isEqualTo(10);
            assertThat(IntStream.range(0, 10)
                .mapToObj(column -> sheet.getRow(0).getCell(column).getStringCellValue()))
                .containsExactly("id", "firstName", "lastName", "email", "phone", "bibNumber",
                    "status", "gender", "nationality", "registeredAt");
            assertThat(sheet.getRow(1).getPhysicalNumberOfCells()).isEqualTo(10);
            assertThat(IntStream.range(0, 10)
                .mapToObj(column -> sheet.getRow(1).getCell(column).getStringCellValue()))
                .containsExactly(participant.id().toString(), "A, \"Runner\"", "Doe", "a@example.com",
                    "123", "B7", "REGISTERED", "", "ZW", "2026-01-02T03:04Z");
        }
        verify(guard).assertCanManage(raceId);
    }

    @Test
    void exactlyTenThousandParticipantsAreAllowedWithBoundedFetch() {
        when(participants.countByRaceId(raceId)).thenReturn(10_000L);
        when(participants.findExportRowsByRaceId(eq(raceId), any(Pageable.class))).thenReturn(List.of());

        service.export(raceId, "xlsx");

        var order = inOrder(participants);
        order.verify(participants).countByRaceId(raceId);
        order.verify(participants).findExportRowsByRaceId(raceId, PageRequest.of(0, 10_001));
    }

    @ParameterizedTest
    @ValueSource(strings = {"csv", "xlsx"})
    void overPilotCapIsRejectedBeforeFetching(String format) {
        when(participants.countByRaceId(raceId)).thenReturn(10_001L);

        assertThatThrownBy(() -> service.export(raceId, format))
            .isInstanceOf(ApiException.class)
            .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("EXPORT_TOO_LARGE"));
        verify(participants).countByRaceId(raceId);
        verify(participants, never()).findExportRowsByRaceId(eq(raceId), any(Pageable.class));
    }

    @Test
    void concurrentGrowthPastCapIsRejectedAfterBoundedFetch() {
        when(participants.countByRaceId(raceId)).thenReturn(10_000L);
        when(participants.findExportRowsByRaceId(eq(raceId), any(Pageable.class)))
            .thenReturn(Collections.nCopies(10_001, participant()));

        assertThatThrownBy(() -> service.export(raceId, "xlsx"))
            .isInstanceOf(ApiException.class)
            .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("EXPORT_TOO_LARGE"));
    }

    @Test
    void legacyCsvServiceEntryPointAlsoHonorsCap() {
        when(participants.countByRaceId(raceId)).thenReturn(10_001L);

        assertThatThrownBy(() -> service.exportCsv(raceId))
            .isInstanceOf(ApiException.class)
            .satisfies(ex -> assertThat(((ApiException) ex).getCode()).isEqualTo("EXPORT_TOO_LARGE"));
        verify(participants, never()).findExportRowsByRaceId(eq(raceId), any(Pageable.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"csv", "xlsx"})
    void generationFailureIsServerError(String format) {
        List<ParticipantExportRow> brokenRows = new AbstractList<>() {
            @Override
            public ParticipantExportRow get(int index) {
                throw new ApiException("Writer I/O details");
            }

            @Override
            public int size() {
                return 1;
            }
        };
        when(participants.findExportRowsByRaceId(eq(raceId), any(Pageable.class))).thenReturn(brokenRows);

        assertThatThrownBy(() -> service.export(raceId, format))
            .isInstanceOf(ServiceUnavailableException.class)
            .hasCauseInstanceOf(ApiException.class)
            .satisfies(ex -> assertThat(((ServiceUnavailableException) ex).getCode())
                .isEqualTo("EXPORT_GENERATION_FAILED"));
    }

    @Test
    void poiWorkbookFailureIsServerError() {
        ParticipantExportRow participant = participant("x".repeat(32_768));
        when(participants.findExportRowsByRaceId(eq(raceId), any(Pageable.class)))
            .thenReturn(List.of(participant));

        assertThatThrownBy(() -> service.export(raceId, "xlsx"))
            .isInstanceOf(ServiceUnavailableException.class)
            .hasCauseInstanceOf(ApiException.class)
            .satisfies(ex -> assertThat(((ServiceUnavailableException) ex).getCode())
                .isEqualTo("EXPORT_GENERATION_FAILED"));
    }

    @Test
    void unsupportedFormatFailsWithoutReadingParticipants() {
        assertThatThrownBy(() -> service.export(raceId, "pdf"))
            .isInstanceOf(ApiException.class).hasMessageContaining("Unsupported export format");
        verify(guard).assertCanManage(raceId);
        verifyNoInteractions(participants);
    }

    private ParticipantExportRow participant() {
        return participant("A, \"Runner\"");
    }

    private ParticipantExportRow participant(String firstName) {
        return new ParticipantExportRow(UUID.randomUUID(), firstName, "Doe", "a@example.com", "123", "B7",
            Participant.ParticipantStatus.REGISTERED, null, "ZW",
            OffsetDateTime.parse("2026-01-02T03:04:00Z"));
    }
}
