package com.racetracker.service.impl;

import com.racetracker.dto.race.RaceStageDto;
import com.racetracker.dto.race.RaceStageRequest;
import com.racetracker.exception.ConflictException;
import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.model.RaceStage;
import com.racetracker.repository.RaceRepository;
import com.racetracker.repository.RaceStageRepository;
import com.racetracker.service.RaceStageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class RaceStageServiceImpl implements RaceStageService {

    private final RaceRepository raceRepository;
    private final RaceStageRepository stageRepository;

    public RaceStageServiceImpl(RaceRepository raceRepository, RaceStageRepository stageRepository) {
        this.raceRepository = raceRepository;
        this.stageRepository = stageRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RaceStageDto> list(UUID raceId) {
        requireRace(raceId);
        return stageRepository.findByRaceIdOrderBySequenceAsc(raceId).stream().map(this::toDto).toList();
    }

    @Override
    @Transactional
    public RaceStageDto create(UUID raceId, RaceStageRequest request) {
        var race = requireRace(raceId);
        if (stageRepository.existsByRaceIdAndSequence(raceId, request.sequence())) {
            throw new ConflictException("A stage already uses this sequence", "RACE_STAGE_SEQUENCE_CONFLICT");
        }
        RaceStage stage = new RaceStage();
        stage.setRace(race);
        apply(stage, request);
        return toDto(stageRepository.save(stage));
    }

    @Override
    @Transactional
    public RaceStageDto update(UUID raceId, UUID stageId, RaceStageRequest request) {
        RaceStage stage = stageRepository.findByRaceIdAndId(raceId, stageId)
            .orElseThrow(() -> new ResourceNotFoundException("Race stage not found"));
        if (!stage.getSequence().equals(request.sequence())
            && stageRepository.existsByRaceIdAndSequence(raceId, request.sequence())) {
            throw new ConflictException("A stage already uses this sequence", "RACE_STAGE_SEQUENCE_CONFLICT");
        }
        apply(stage, request);
        return toDto(stageRepository.save(stage));
    }

    @Override
    @Transactional
    public void delete(UUID raceId, UUID stageId) {
        RaceStage stage = stageRepository.findByRaceIdAndId(raceId, stageId)
            .orElseThrow(() -> new ResourceNotFoundException("Race stage not found"));
        stageRepository.delete(stage);
    }

    private void apply(RaceStage stage, RaceStageRequest request) {
        stage.setName(request.name().trim());
        stage.setSequence(request.sequence());
        stage.setStageDate(request.stageDate());
        stage.setStartTime(request.startTime());
        stage.setDistanceKm(request.distanceKm());
        stage.setRouteDescription(request.routeDescription());
    }

    private com.racetracker.model.Race requireRace(UUID raceId) {
        return raceRepository.findById(raceId)
            .orElseThrow(() -> new ResourceNotFoundException("Race not found"));
    }

    private RaceStageDto toDto(RaceStage stage) {
        return new RaceStageDto(stage.getId(), stage.getRace().getId(), stage.getName(), stage.getSequence(),
            stage.getStageDate(), stage.getStartTime(), stage.getDistanceKm(), stage.getRouteDescription());
    }
}
