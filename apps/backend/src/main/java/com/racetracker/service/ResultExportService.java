package com.racetracker.service;

import java.util.UUID;

public interface ResultExportService {

    ExportedFile export(UUID raceId, String format);

    record ExportedFile(byte[] content, String contentType, String filename) {}
}
