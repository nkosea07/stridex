package com.racetracker.dto.participant;

import java.util.List;
import java.util.Map;

public record ImportPreviewDto(int totalRows, int validRows, int duplicateRows,
                               List<Row> rows) {
    public record Row(int rowNumber, Map<String, String> values, List<String> errors) {}
}
