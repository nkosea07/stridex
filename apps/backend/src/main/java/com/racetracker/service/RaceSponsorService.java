package com.racetracker.service;

import com.racetracker.dto.race.RaceSponsorDto;
import com.racetracker.dto.race.RaceSponsorRequest;

import java.util.List;
import java.util.UUID;

public interface RaceSponsorService {
    List<RaceSponsorDto> list(UUID raceId);
    RaceSponsorDto create(UUID raceId, RaceSponsorRequest request);
    RaceSponsorDto update(UUID raceId, UUID sponsorId, RaceSponsorRequest request);
    void delete(UUID raceId, UUID sponsorId);
}
