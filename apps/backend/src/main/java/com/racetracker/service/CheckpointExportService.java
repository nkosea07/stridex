package com.racetracker.service;

import java.util.UUID;

public interface CheckpointExportService {

    ExportedFile export(UUID raceId, String format);
}
