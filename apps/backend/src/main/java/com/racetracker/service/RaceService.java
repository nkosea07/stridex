package com.racetracker.service;

import com.racetracker.dto.race.RaceCloneRequest;
import com.racetracker.dto.race.RaceCreateRequest;
import com.racetracker.dto.race.RaceDto;
import com.racetracker.dto.race.RaceUpdateRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface RaceService {
    default Page<RaceDto> list(String status, Boolean upcoming, Pageable pageable) {
        return list(status, upcoming, false, pageable);
    }
    Page<RaceDto> list(String status, Boolean upcoming, boolean includeArchived, Pageable pageable);
    RaceDto getById(UUID id);
    RaceDto create(RaceCreateRequest request);
    RaceDto clone(UUID id, RaceCloneRequest request);
    RaceDto update(UUID id, RaceUpdateRequest request);
    RaceDto archive(UUID id);
    RaceDto restore(UUID id);
    void delete(UUID id);
    RaceDto updateStatus(UUID id, String status);
}
