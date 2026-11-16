package com.racetracker.service;

import com.racetracker.dto.race.RaceWaveDto;
import com.racetracker.dto.race.RaceWaveRequest;
import com.racetracker.dto.race.WaveAssignmentRequest;
import com.racetracker.dto.race.WaveStartRequest;

import java.util.List;
import java.util.UUID;

public interface WaveService {
    List<RaceWaveDto> list(UUID raceId);
    RaceWaveDto create(UUID raceId, RaceWaveRequest request);
    RaceWaveDto update(UUID raceId, UUID waveId, RaceWaveRequest request);
    void delete(UUID raceId, UUID waveId);
    RaceWaveDto start(UUID raceId, UUID waveId, WaveStartRequest request);
    int assign(UUID raceId, UUID waveId, WaveAssignmentRequest request);
}
