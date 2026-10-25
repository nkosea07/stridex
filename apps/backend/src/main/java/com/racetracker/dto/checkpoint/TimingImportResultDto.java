package com.racetracker.dto.checkpoint;

import java.util.List;

public record TimingImportResultDto(int totalRows, int imported, List<RowError> errors) {
    public record RowError(int rowNumber, String message) {}
}
