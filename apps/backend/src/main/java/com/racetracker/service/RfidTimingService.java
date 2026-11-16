package com.racetracker.service;

import com.racetracker.dto.rfid.GunTimeDto;
import com.racetracker.dto.rfid.GunTimeRequest;
import com.racetracker.dto.rfid.RfidReprocessRequest;
import com.racetracker.dto.rfid.RfidReprocessResultDto;
import com.racetracker.dto.rfid.RfidUnmatchedTagDto;

import java.util.List;
import java.util.UUID;

public interface RfidTimingService {

    List<RfidUnmatchedTagDto> unmatchedTags(UUID raceId);

    RfidReprocessResultDto reprocess(UUID raceId, RfidReprocessRequest request, String actorEmail);

    GunTimeDto gunTime(UUID raceId);

    GunTimeDto correctGunTime(UUID raceId, GunTimeRequest request, String actorEmail);
}
