package com.racetracker.service.impl;

import com.racetracker.dto.checkpoint.CheckpointDto;
import com.racetracker.exception.ApiException;
import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.model.Race;
import com.racetracker.repository.RaceRepository;
import com.racetracker.service.CheckpointExportService;
import com.racetracker.service.CheckpointService;
import com.racetracker.service.ExportedFile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class CheckpointExportServiceImpl implements CheckpointExportService {

    private static final String[] HEADERS = {
        "Order", "Name", "Type", "Distance (km)", "Cutoff", "RFID", "QR Scanner", "Records"
    };

    private final CheckpointService checkpointService;
    private final RaceRepository raceRepository;

    public CheckpointExportServiceImpl(CheckpointService checkpointService, RaceRepository raceRepository) {
        this.checkpointService = checkpointService;
        this.raceRepository = raceRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public ExportedFile export(UUID raceId, String format) {
        Race race = raceRepository.findById(raceId)
            .orElseThrow(() -> new ResourceNotFoundException("Race not found"));
        List<String[]> rows = toRows(checkpointService.listByRace(raceId));
        String safeName = (race.getName() == null ? "race" : race.getName())
            .replaceAll("[^a-zA-Z0-9-_]+", "-")
            .toLowerCase(Locale.ROOT);
        String normalized = format == null ? "csv" : format.trim().toLowerCase(Locale.ROOT);

        return switch (normalized) {
            case "csv" -> new ExportedFile(TabularExportWriter.csv(rows), "text/csv", safeName + "-checkpoints.csv");
            case "xlsx" -> new ExportedFile(TabularExportWriter.xlsx("Checkpoints", rows),
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", safeName + "-checkpoints.xlsx");
            default -> throw new ApiException("Unsupported export format: " + format + " (use csv or xlsx)");
        };
    }

    private List<String[]> toRows(List<CheckpointDto> checkpoints) {
        List<String[]> rows = new ArrayList<>(checkpoints.size() + 1);
        rows.add(HEADERS);
        for (CheckpointDto checkpoint : checkpoints) {
            rows.add(new String[] {
                value(checkpoint.getOrderIndex()),
                value(checkpoint.getName()),
                value(checkpoint.getCheckpointType()),
                value(checkpoint.getDistanceFromStartKm()),
                value(checkpoint.getCutoffTime()),
                value(Boolean.TRUE.equals(checkpoint.getHasRfidReader())),
                value(Boolean.TRUE.equals(checkpoint.getHasQrScanner())),
                value(checkpoint.getRecordCount())
            });
        }
        return rows;
    }

    private String value(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
