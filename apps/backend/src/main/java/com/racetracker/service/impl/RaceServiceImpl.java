package com.racetracker.service.impl;

import com.racetracker.dto.participant.RaceRegistrationFieldDto;
import com.racetracker.dto.race.RaceCloneRequest;
import com.racetracker.dto.race.RaceCreateRequest;
import com.racetracker.dto.race.RaceCategoryRequest;
import com.racetracker.dto.race.RaceDto;
import com.racetracker.dto.race.RaceUpdateRequest;
import com.racetracker.exception.ApiException;
import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.mapper.RaceMapper;
import com.racetracker.model.Checkpoint;
import com.racetracker.model.Race;
import com.racetracker.model.RaceCategory;
import com.racetracker.model.RaceRegistrationField;
import com.racetracker.repository.RaceRegistrationFieldRepository;
import com.racetracker.repository.RaceRepository;
import com.racetracker.service.RaceService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class RaceServiceImpl implements RaceService {

    private final RaceRepository raceRepository;
    private final RaceMapper raceMapper;
    private final RaceRegistrationFieldRepository registrationFieldRepository;

    public RaceServiceImpl(
        RaceRepository raceRepository,
        RaceMapper raceMapper,
        RaceRegistrationFieldRepository registrationFieldRepository
    ) {
        this.raceRepository = raceRepository;
        this.raceMapper = raceMapper;
        this.registrationFieldRepository = registrationFieldRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<RaceDto> list(String status, Boolean upcoming, boolean includeArchived, Pageable pageable) {
        boolean onlyUpcoming = Boolean.TRUE.equals(upcoming);
        Race.RaceStatus parsedStatus = parseRaceStatusNullable(status);

        Page<Race> races;
        if (includeArchived) {
            if (onlyUpcoming && parsedStatus != null) {
                races = raceRepository.findByStatusAndRaceDateGreaterThanEqual(parsedStatus, LocalDate.now(), pageable);
            } else if (onlyUpcoming) {
                races = raceRepository.findByRaceDateGreaterThanEqual(LocalDate.now(), pageable);
            } else if (parsedStatus != null) {
                races = raceRepository.findByStatus(parsedStatus, pageable);
            } else {
                races = raceRepository.findAll(pageable);
            }
        } else if (onlyUpcoming && parsedStatus != null) {
            races = raceRepository.findByStatusAndRaceDateGreaterThanEqualAndArchivedAtIsNull(parsedStatus, LocalDate.now(), pageable);
        } else if (onlyUpcoming) {
            races = raceRepository.findByRaceDateGreaterThanEqualAndArchivedAtIsNull(LocalDate.now(), pageable);
        } else if (parsedStatus != null) {
            races = raceRepository.findByStatusAndArchivedAtIsNull(parsedStatus, pageable);
        } else {
            races = raceRepository.findAllByArchivedAtIsNull(pageable);
        }

        return races.map(raceMapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public RaceDto getById(UUID id) {
        Race race = raceRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Race not found"));
        RaceDto dto = raceMapper.toDto(race);
        dto.setRegistrationFields(registrationFieldRepository.findByRaceIdOrderByDisplayOrderAsc(id)
            .stream()
            .map(this::toFieldDto)
            .toList());
        return dto;
    }

    private RaceRegistrationFieldDto toFieldDto(RaceRegistrationField field) {
        RaceRegistrationFieldDto dto = new RaceRegistrationFieldDto();
        dto.setId(field.getId());
        dto.setRaceId(field.getRace() != null ? field.getRace().getId() : null);
        dto.setFieldKey(field.getFieldKey());
        dto.setLabel(field.getLabel());
        dto.setFieldType(field.getFieldType() != null ? field.getFieldType().name() : null);
        dto.setRequired(Boolean.TRUE.equals(field.getRequired()));
        dto.setOptions(field.getOptions() != null ? field.getOptions() : List.of());
        dto.setDisplayOrder(field.getDisplayOrder());
        dto.setActive(Boolean.TRUE.equals(field.getActive()));
        dto.setCreatedAt(field.getCreatedAt());
        return dto;
    }

    @Override
    @Transactional
    public RaceDto create(RaceCreateRequest request) {
        Race race = new Race();
        applyCommonFields(request, race);

        race.getCategories().clear();
        if (request.getCategories() != null) {
            request.getCategories().forEach(category -> race.getCategories().add(toCategoryEntity(race, category)));
        }

        Race saved = raceRepository.save(race);
        return raceMapper.toDto(saved);
    }

    @Override
    @Transactional
    public RaceDto update(UUID id, RaceUpdateRequest request) {
        Race race = raceRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Race not found"));

        applyCommonFields(request, race);

        if (request.getCategories() != null) {
            race.getCategories().clear();
            request.getCategories().forEach(category -> race.getCategories().add(toCategoryEntity(race, category)));
        }

        Race saved = raceRepository.save(race);
        return raceMapper.toDto(saved);
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        Race race = raceRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Race not found"));
        raceRepository.delete(race);
    }

    @Override
    @Transactional
    public RaceDto updateStatus(UUID id, String status) {
        Race race = raceRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Race not found"));
        race.setStatus(parseRaceStatus(status));
        Race saved = raceRepository.save(race);
        return raceMapper.toDto(saved);
    }

    @Override
    @Transactional
    public RaceDto clone(UUID id, RaceCloneRequest request) {
        Race source = raceRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Race not found"));

        Race copy = new Race();
        copy.setName(request.getName());
        copy.setDescription(source.getDescription());
        copy.setRaceType(source.getRaceType());
        copy.setStatus(Race.RaceStatus.DRAFT);
        copy.setCity(source.getCity());
        copy.setCountry(source.getCountry());
        copy.setStartLocationLat(source.getStartLocationLat());
        copy.setStartLocationLng(source.getStartLocationLng());
        copy.setFinishLocationLat(source.getFinishLocationLat());
        copy.setFinishLocationLng(source.getFinishLocationLng());
        copy.setRaceDate(request.getRaceDate() != null ? request.getRaceDate() : source.getRaceDate());
        copy.setStartTime(request.getStartTime() != null ? request.getStartTime() : source.getStartTime());
        copy.setRegistrationOpens(request.getRegistrationOpens() != null ? request.getRegistrationOpens() : source.getRegistrationOpens());
        copy.setRegistrationCloses(request.getRegistrationCloses() != null ? request.getRegistrationCloses() : source.getRegistrationCloses());
        copy.setDistanceKm(source.getDistanceKm());
        copy.setMaxParticipants(source.getMaxParticipants());
        copy.setRegistrationFee(source.getRegistrationFee());
        copy.setCurrency(source.getCurrency());
        copy.setBannerImageUrl(source.getBannerImageUrl());
        copy.setLogoUrl(source.getLogoUrl());
        copy.setIsPublicRegistration(source.getIsPublicRegistration());
        copy.setRequireMedicalClearance(source.getRequireMedicalClearance());
        copy.setRequireApproval(source.getRequireApproval());
        copy.setWaitlistEnabled(source.getWaitlistEnabled());
        copy.setRequireWaiver(source.getRequireWaiver());
        copy.setWaiverVersion(source.getWaiverVersion());
        copy.setWaiverText(source.getWaiverText());
        copy.setResultsPublishedAt(null);
        copy.setArchivedAt(null);

        if (Boolean.TRUE.equals(request.getCopyCategories())) {
            source.getCategories().forEach(category -> copy.getCategories().add(cloneCategory(copy, category)));
        }
        if (Boolean.TRUE.equals(request.getCopyCheckpoints())) {
            source.getCheckpoints().forEach(checkpoint -> copy.getCheckpoints().add(cloneCheckpoint(copy, checkpoint)));
        }

        Race saved = raceRepository.save(copy);

        if (Boolean.TRUE.equals(request.getCopyRegistrationFields())) {
            List<RaceRegistrationField> sourceFields =
                registrationFieldRepository.findByRaceIdOrderByDisplayOrderAsc(source.getId());
            sourceFields.forEach(field -> registrationFieldRepository.save(cloneRegistrationField(saved, field)));
        }

        return raceMapper.toDto(saved);
    }

    @Override
    @Transactional
    public RaceDto archive(UUID id) {
        Race race = raceRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Race not found"));
        race.setArchivedAt(OffsetDateTime.now());
        return raceMapper.toDto(raceRepository.save(race));
    }

    @Override
    @Transactional
    public RaceDto restore(UUID id) {
        Race race = raceRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Race not found"));
        race.setArchivedAt(null);
        return raceMapper.toDto(raceRepository.save(race));
    }

    private RaceCategory cloneCategory(Race race, RaceCategory source) {
        return RaceCategory.builder()
            .race(race)
            .name(source.getName())
            .description(source.getDescription())
            .minAge(source.getMinAge())
            .maxAge(source.getMaxAge())
            .gender(source.getGender())
            .bibPrefix(source.getBibPrefix())
            .maxParticipants(source.getMaxParticipants())
            .registrationFee(source.getRegistrationFee())
            .build();
    }

    private Checkpoint cloneCheckpoint(Race race, Checkpoint source) {
        return Checkpoint.builder()
            .race(race)
            .name(source.getName())
            .checkpointType(source.getCheckpointType())
            .distanceFromStartKm(source.getDistanceFromStartKm())
            .latitude(source.getLatitude())
            .longitude(source.getLongitude())
            .orderIndex(source.getOrderIndex())
            .cutoffTime(source.getCutoffTime())
            .hasRfidReader(Boolean.TRUE.equals(source.getHasRfidReader()))
            .hasQrScanner(Boolean.TRUE.equals(source.getHasQrScanner()))
            .build();
    }

    private RaceRegistrationField cloneRegistrationField(Race race, RaceRegistrationField source) {
        return RaceRegistrationField.builder()
            .race(race)
            .fieldKey(source.getFieldKey())
            .label(source.getLabel())
            .fieldType(source.getFieldType())
            .required(Boolean.TRUE.equals(source.getRequired()))
            .options(source.getOptions() != null ? new java.util.ArrayList<>(source.getOptions()) : new java.util.ArrayList<>())
            .displayOrder(source.getDisplayOrder())
            .active(Boolean.TRUE.equals(source.getActive()))
            .build();
    }

    private void applyCommonFields(RaceCreateRequest request, Race race) {
        race.setName(request.getName());
        race.setDescription(request.getDescription());
        race.setRaceType(parseRaceType(request.getRaceType()));
        race.setCity(request.getCity());
        race.setCountry(request.getCountry());
        race.setStartLocationLat(request.getStartLocationLat());
        race.setStartLocationLng(request.getStartLocationLng());
        race.setFinishLocationLat(request.getFinishLocationLat());
        race.setFinishLocationLng(request.getFinishLocationLng());
        race.setRaceDate(request.getRaceDate());
        race.setStartTime(request.getStartTime());
        race.setRegistrationOpens(request.getRegistrationOpens());
        race.setRegistrationCloses(request.getRegistrationCloses());
        race.setDistanceKm(request.getDistanceKm());
        race.setMaxParticipants(request.getMaxParticipants());
        race.setRegistrationFee(request.getRegistrationFee());
        race.setCurrency(request.getCurrency() == null ? "USD" : request.getCurrency());
        race.setBannerImageUrl(request.getBannerImageUrl());
        race.setLogoUrl(request.getLogoUrl());
        race.setIsPublicRegistration(request.getIsPublicRegistration() != null ? request.getIsPublicRegistration() : true);
        race.setRequireMedicalClearance(request.getRequireMedicalClearance() != null ? request.getRequireMedicalClearance() : false);
        race.setRequireApproval(request.getRequireApproval() != null ? request.getRequireApproval() : false);
        race.setWaitlistEnabled(request.getWaitlistEnabled() != null ? request.getWaitlistEnabled() : false);
        race.setRequireWaiver(request.getRequireWaiver() != null ? request.getRequireWaiver() : false);
        race.setWaiverVersion(request.getWaiverVersion());
        race.setWaiverText(request.getWaiverText());
        if (Boolean.TRUE.equals(race.getRequireWaiver())
            && (race.getWaiverText() == null || race.getWaiverText().isBlank())) {
            throw new ApiException("Waiver text is required when a waiver is enforced");
        }
    }

    private void applyCommonFields(RaceUpdateRequest request, Race race) {
        if (request.getName() != null) race.setName(request.getName());
        if (request.getDescription() != null) race.setDescription(request.getDescription());
        if (request.getRaceType() != null) race.setRaceType(parseRaceType(request.getRaceType()));
        if (request.getCity() != null) race.setCity(request.getCity());
        if (request.getCountry() != null) race.setCountry(request.getCountry());
        if (request.getStartLocationLat() != null) race.setStartLocationLat(request.getStartLocationLat());
        if (request.getStartLocationLng() != null) race.setStartLocationLng(request.getStartLocationLng());
        if (request.getFinishLocationLat() != null) race.setFinishLocationLat(request.getFinishLocationLat());
        if (request.getFinishLocationLng() != null) race.setFinishLocationLng(request.getFinishLocationLng());
        if (request.getRaceDate() != null) race.setRaceDate(request.getRaceDate());
        if (request.getStartTime() != null) race.setStartTime(request.getStartTime());
        if (request.getRegistrationOpens() != null) race.setRegistrationOpens(request.getRegistrationOpens());
        if (request.getRegistrationCloses() != null) race.setRegistrationCloses(request.getRegistrationCloses());
        if (request.getDistanceKm() != null) race.setDistanceKm(request.getDistanceKm());
        if (request.getMaxParticipants() != null) race.setMaxParticipants(request.getMaxParticipants());
        if (request.getRegistrationFee() != null) race.setRegistrationFee(request.getRegistrationFee());
        if (request.getCurrency() != null) race.setCurrency(request.getCurrency());
        if (request.getBannerImageUrl() != null) race.setBannerImageUrl(request.getBannerImageUrl());
        if (request.getLogoUrl() != null) race.setLogoUrl(request.getLogoUrl());
        if (request.getIsPublicRegistration() != null) race.setIsPublicRegistration(request.getIsPublicRegistration());
        if (request.getRequireMedicalClearance() != null) race.setRequireMedicalClearance(request.getRequireMedicalClearance());
        if (request.getRequireApproval() != null) race.setRequireApproval(request.getRequireApproval());
        if (request.getWaitlistEnabled() != null) race.setWaitlistEnabled(request.getWaitlistEnabled());
        if (request.getRequireWaiver() != null) race.setRequireWaiver(request.getRequireWaiver());
        if (request.getWaiverVersion() != null) race.setWaiverVersion(request.getWaiverVersion());
        if (request.getWaiverText() != null) race.setWaiverText(request.getWaiverText());
        if (Boolean.TRUE.equals(race.getRequireWaiver())
            && (race.getWaiverText() == null || race.getWaiverText().isBlank())) {
            throw new ApiException("Waiver text is required when a waiver is enforced");
        }
    }

    private RaceCategory toCategoryEntity(Race race, RaceCategoryRequest request) {
        return RaceCategory.builder()
            .race(race)
            .name(request.getName())
            .description(request.getDescription())
            .minAge(request.getMinAge())
            .maxAge(request.getMaxAge())
            .gender(request.getGender())
            .bibPrefix(request.getBibPrefix())
            .maxParticipants(request.getMaxParticipants())
            .registrationFee(request.getRegistrationFee())
            .build();
    }

    private Race.RaceType parseRaceType(String input) {
        if (input == null || input.isBlank()) {
            throw new ApiException("Race type is required");
        }
        String normalized = input.trim().toUpperCase(Locale.ROOT)
            .replace('-', '_')
            .replace(' ', '_');

        if ("10K".equalsIgnoreCase(input)) {
            return Race.RaceType.TEN_K;
        }
        if ("5K".equalsIgnoreCase(input)) {
            return Race.RaceType.FIVE_K;
        }

        try {
            return Race.RaceType.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            throw new ApiException("Invalid race type: " + input);
        }
    }

    private Race.RaceStatus parseRaceStatus(String input) {
        if (input == null || input.isBlank()) {
            throw new ApiException("Status is required");
        }
        try {
            return Race.RaceStatus.valueOf(input.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ApiException("Invalid race status: " + input);
        }
    }

    private Race.RaceStatus parseRaceStatusNullable(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        return parseRaceStatus(input);
    }
}
