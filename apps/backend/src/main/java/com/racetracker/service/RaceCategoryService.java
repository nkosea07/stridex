package com.racetracker.service;

import com.racetracker.dto.race.RaceCategoryDto;
import com.racetracker.dto.race.RaceCategoryRequest;

import java.util.List;
import java.util.UUID;

public interface RaceCategoryService {

    List<RaceCategoryDto> list(UUID raceId);

    RaceCategoryDto create(UUID raceId, RaceCategoryRequest request);

    RaceCategoryDto update(UUID raceId, UUID categoryId, RaceCategoryRequest request);

    void delete(UUID raceId, UUID categoryId);
}
