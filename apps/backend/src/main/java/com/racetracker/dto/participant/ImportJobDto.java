package com.racetracker.dto.participant;

import lombok.Data;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data
public class ImportJobDto {
    private UUID id;
    private UUID raceId;
    private String fileName;
    private String status;
    private Integer totalRows;
    private Integer processedRows;
    private Integer successCount;
    private Integer errorCount;
    private String errorMessage;
    private java.util.List<RowError> rowErrors = new java.util.ArrayList<>();

    public record RowError(int row, String reason) {
    }
    private OffsetDateTime createdAt;
    private OffsetDateTime startedAt;
    private OffsetDateTime completedAt;
}
