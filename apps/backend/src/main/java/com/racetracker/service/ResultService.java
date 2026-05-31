package com.racetracker.service;

import com.racetracker.dto.result.RaceResultDto;

import java.util.List;
import java.util.UUID;

public interface ResultService {
    List<RaceResultDto> listByRace(UUID raceId);
    RaceResultDto getByRaceAndParticipant(UUID raceId, UUID participantId);
    List<RaceResultDto> getMyResults();
    void publish(UUID raceId);
}
