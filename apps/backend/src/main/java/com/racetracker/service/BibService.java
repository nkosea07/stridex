package com.racetracker.service;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.UUID;

public interface BibService {
    byte[] generateBibPdf(UUID raceId, UUID participantId);
    byte[] generateBatchBibsPdf(UUID raceId, List<UUID> participantIds);
}
