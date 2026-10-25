package com.racetracker.service;

import com.racetracker.dto.race.RaceStageDto;
import com.racetracker.dto.race.RaceStageRequest;

import java.util.List;
import java.util.UUID;

public interface RaceStageService {
    List<RaceStageDto> list(UUID raceId);
    RaceStageDto create(UUID raceId, RaceStageRequest request);
    RaceStageDto update(UUID raceId, UUID stageId, RaceStageRequest request);
    void delete(UUID raceId, UUID stageId);
}
