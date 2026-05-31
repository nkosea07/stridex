package com.racetracker.service;

import com.racetracker.dto.race.RaceCreateRequest;
import com.racetracker.dto.race.RaceDto;
import com.racetracker.dto.race.RaceUpdateRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface RaceService {
    Page<RaceDto> list(String status, Boolean upcoming, Pageable pageable);
    RaceDto getById(UUID id);
    RaceDto create(RaceCreateRequest request);
    RaceDto update(UUID id, RaceUpdateRequest request);
    void delete(UUID id);
    RaceDto updateStatus(UUID id, String status);
}
