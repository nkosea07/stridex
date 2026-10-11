package com.racetracker.service;

import java.util.UUID;

public interface AnalyticsExportService {

    ExportedFile export(UUID raceId, String format);
}
