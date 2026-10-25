package com.racetracker.service.impl;

import com.opencsv.CSVReader;
import com.racetracker.dto.checkpoint.CheckpointRecordRequest;
import com.racetracker.dto.checkpoint.TimingImportResultDto;
import com.racetracker.exception.ApiException;
import com.racetracker.service.CheckpointService;
import com.racetracker.service.TimingImportService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class TimingImportServiceImpl implements TimingImportService {

    private final CheckpointService checkpointService;

    public TimingImportServiceImpl(CheckpointService checkpointService) {
        this.checkpointService = checkpointService;
    }

    @Override
    public TimingImportResultDto importCsv(UUID raceId, org.springframework.web.multipart.MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException("A timing import file is required");
        }
        try (CSVReader reader = new CSVReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            List<String[]> rows = reader.readAll();
            if (rows.isEmpty()) {
                return new TimingImportResultDto(0, 0, List.of());
            }
            var header = new java.util.HashMap<String, Integer>();
            for (int i = 0; i < rows.get(0).length; i++) {
                header.put(rows.get(0)[i].trim(), i);
            }
            List<TimingImportResultDto.RowError> errors = new ArrayList<>();
            int imported = 0;
            for (int rowIndex = 1; rowIndex < rows.size(); rowIndex++) {
                String[] row = rows.get(rowIndex);
                try {
                    String checkpointId = cell(row, header, "checkpointId");
                    String bib = cell(row, header, "participantBibNumber");
                    String recordedAt = cell(row, header, "recordedAt");
                    if (checkpointId == null || checkpointId.isBlank() || bib == null || bib.isBlank()) {
                        throw new IllegalArgumentException("checkpointId and participantBibNumber are required");
                    }
                    CheckpointRecordRequest request = new CheckpointRecordRequest();
                    request.setParticipantBibNumber(bib);
                    request.setMethod(cell(row, header, "method"));
                    if (recordedAt != null && !recordedAt.isBlank()) {
                        request.setRecordedAt(OffsetDateTime.parse(recordedAt));
                    }
                    checkpointService.recordImported(raceId, UUID.fromString(checkpointId), request);
                    imported++;
                } catch (AccessDeniedException ex) {
                    throw ex;
                } catch (Exception ex) {
                    errors.add(new TimingImportResultDto.RowError(rowIndex, ex.getMessage() == null
                        ? "Import row failed" : ex.getMessage()));
                }
            }
            return new TimingImportResultDto(rows.size() - 1, imported, errors);
        } catch (ApiException ex) {
            throw ex;
        } catch (AccessDeniedException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ApiException("Could not read timing import file");
        }
    }

    private String cell(String[] row, java.util.Map<String, Integer> header, String key) {
        Integer index = header.get(key);
        if (index == null || index < 0 || index >= row.length) {
            return null;
        }
        return row[index] == null ? null : row[index].trim();
    }
}
