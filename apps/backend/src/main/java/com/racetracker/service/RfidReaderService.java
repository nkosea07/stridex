package com.racetracker.service;

import com.racetracker.dto.rfid.RfidReaderCreateRequest;
import com.racetracker.dto.rfid.RfidReaderCreateResponse;
import com.racetracker.dto.rfid.RfidReaderDto;
import com.racetracker.dto.rfid.RfidReaderHeartbeatRequest;
import com.racetracker.dto.rfid.RfidRaceOverviewDto;

import java.util.List;
import java.util.UUID;

public interface RfidReaderService {
    RfidReaderCreateResponse create(UUID raceId, RfidReaderCreateRequest request);
    List<RfidReaderDto> list(UUID raceId);
    RfidRaceOverviewDto overview(UUID raceId);
    RfidReaderDto revoke(UUID raceId, UUID readerId);
    RfidReaderDto heartbeat(String apiKey, RfidReaderHeartbeatRequest request, String sourceIp);
}
