package com.racetracker.service.impl;

import com.racetracker.dto.race.RaceCategoryDto;
import com.racetracker.dto.race.RaceCategoryRequest;
import com.racetracker.exception.ApiException;
import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.model.Race;
import com.racetracker.model.RaceCategory;
import com.racetracker.repository.RaceCategoryRepository;
import com.racetracker.repository.RaceRepository;
import com.racetracker.service.RaceCategoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class RaceCategoryServiceImpl implements RaceCategoryService {

    private final RaceRepository raceRepository;
    private final RaceCategoryRepository categoryRepository;

    public RaceCategoryServiceImpl(RaceRepository raceRepository, RaceCategoryRepository categoryRepository) {
        this.raceRepository = raceRepository;
        this.categoryRepository = categoryRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RaceCategoryDto> list(UUID raceId) {
        requireRace(raceId);
        return categoryRepository.findByRaceId(raceId).stream().map(this::toDto).toList();
    }

    @Override
    @Transactional
    public RaceCategoryDto create(UUID raceId, RaceCategoryRequest request) {
        Race race = requireRace(raceId);
        RaceCategory category = RaceCategory.builder()
            .race(race)
            .name(requireName(request))
            .description(request.getDescription())
            .minAge(request.getMinAge())
            .maxAge(request.getMaxAge())
            .gender(normalizeGender(request.getGender()))
            .bibPrefix(request.getBibPrefix())
            .maxParticipants(request.getMaxParticipants())
            .registrationFee(request.getRegistrationFee())
            .build();
        return toDto(categoryRepository.save(category));
    }

    @Override
    @Transactional
    public RaceCategoryDto update(UUID raceId, UUID categoryId, RaceCategoryRequest request) {
        RaceCategory category = categoryRepository.findById(categoryId)
            .filter(existing -> existing.getRace() != null && raceId.equals(existing.getRace().getId()))
            .orElseThrow(() -> new ResourceNotFoundException("Race category not found"));
        category.setName(requireName(request));
        category.setDescription(request.getDescription());
        category.setMinAge(request.getMinAge());
        category.setMaxAge(request.getMaxAge());
        category.setGender(normalizeGender(request.getGender()));
        category.setBibPrefix(request.getBibPrefix());
        category.setMaxParticipants(request.getMaxParticipants());
        category.setRegistrationFee(request.getRegistrationFee());
        return toDto(categoryRepository.save(category));
    }

    @Override
    @Transactional
    public void delete(UUID raceId, UUID categoryId) {
        RaceCategory category = categoryRepository.findById(categoryId)
            .filter(existing -> existing.getRace() != null && raceId.equals(existing.getRace().getId()))
            .orElseThrow(() -> new ResourceNotFoundException("Race category not found"));
        if (!category.getParticipants().isEmpty()) {
            throw new ApiException("Cannot delete a category that has participants");
        }
        categoryRepository.delete(category);
    }

    private Race requireRace(UUID raceId) {
        return raceRepository.findById(raceId)
            .orElseThrow(() -> new ResourceNotFoundException("Race not found"));
    }

    private String requireName(RaceCategoryRequest request) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new ApiException("Category name is required");
        }
        return request.getName().trim();
    }

    private String normalizeGender(String gender) {
        if (gender == null || gender.isBlank()) {
            return null;
        }
        return gender.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private RaceCategoryDto toDto(RaceCategory category) {
        RaceCategoryDto dto = new RaceCategoryDto();
        dto.setId(category.getId());
        dto.setRaceId(category.getRace() != null ? category.getRace().getId() : null);
        dto.setName(category.getName());
        dto.setDescription(category.getDescription());
        dto.setMinAge(category.getMinAge());
        dto.setMaxAge(category.getMaxAge());
        dto.setGender(category.getGender());
        dto.setBibPrefix(category.getBibPrefix());
        dto.setMaxParticipants(category.getMaxParticipants());
        dto.setRegistrationFee(category.getRegistrationFee());
        dto.setParticipantCount(category.getParticipants() != null ? category.getParticipants().size() : 0);
        return dto;
    }
}
