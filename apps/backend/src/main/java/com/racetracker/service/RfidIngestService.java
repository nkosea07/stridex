package com.racetracker.service;

import com.racetracker.dto.rfid.RfidReadEventBatchRequest;
import com.racetracker.dto.rfid.RfidReadEventBatchResponse;

public interface RfidIngestService {
    RfidReadEventBatchResponse ingest(String apiKey, RfidReadEventBatchRequest request, String sourceIp);
}
