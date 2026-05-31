package com.racetracker.service.impl;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import com.racetracker.dto.participant.*;
import com.racetracker.exception.ApiException;
import com.racetracker.exception.ConflictException;
import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.exception.UnauthorizedException;
import com.racetracker.mapper.ImportJobMapper;
import com.racetracker.mapper.ParticipantMapper;
import com.racetracker.model.*;
import com.racetracker.repository.*;
import com.racetracker.service.ParticipantService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class ParticipantServiceImpl implements ParticipantService {

    private final ParticipantRepository participantRepository;
    private final RaceRepository raceRepository;
    private final RaceCategoryRepository raceCategoryRepository;
    private final UserRepository userRepository;
    private final ImportJobRepository importJobRepository;
    private final ParticipantMapper participantMapper;
    private final ImportJobMapper importJobMapper;
    private final com.racetracker.config.RaceTrackerProperties raceTrackerProperties;

    public ParticipantServiceImpl(
        ParticipantRepository participantRepository,
        RaceRepository raceRepository,
        RaceCategoryRepository raceCategoryRepository,
        UserRepository userRepository,
        ImportJobRepository importJobRepository,
        ParticipantMapper participantMapper,
        ImportJobMapper importJobMapper,
        com.racetracker.config.RaceTrackerProperties raceTrackerProperties
    ) {
        this.participantRepository = participantRepository;
        this.raceRepository = raceRepository;
        this.raceCategoryRepository = raceCategoryRepository;
        this.userRepository = userRepository;
        this.importJobRepository = importJobRepository;
        this.participantMapper = participantMapper;
        this.importJobMapper = importJobMapper;
        this.raceTrackerProperties = raceTrackerProperties;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ParticipantDto> listByRace(UUID raceId) {
        verifyRaceExists(raceId);
        return participantRepository.findByRaceIdOrderByRegisteredAtDesc(raceId)
            .stream()
            .map(participantMapper::toDto)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ParticipantDto getByRaceAndId(UUID raceId, UUID participantId) {
        Participant participant = getParticipant(raceId, participantId);
        return participantMapper.toDto(participant);
    }

    @Override
    @Transactional
    public ParticipantDto create(UUID raceId, ParticipantCreateRequest request) {
        Race race = getRace(raceId);
        Participant participant = new Participant();
        participant.setRace(race);
        applyCreateFields(participant, request);

        if (participant.getStatus() == null) {
            participant.setStatus(Participant.ParticipantStatus.REGISTERED);
        }
        if (participant.getRegisteredAt() == null) {
            participant.setRegisteredAt(OffsetDateTime.now());
        }

        Participant saved = participantRepository.save(participant);
        return participantMapper.toDto(saved);
    }

    @Override
    @Transactional
    public ParticipantDto update(UUID raceId, UUID participantId, ParticipantUpdateRequest request) {
        Participant participant = getParticipant(raceId, participantId);
        applyUpdateFields(participant, request);

        Participant saved = participantRepository.save(participant);
        return participantMapper.toDto(saved);
    }

    @Override
    @Transactional
    public void delete(UUID raceId, UUID participantId) {
        Participant participant = getParticipant(raceId, participantId);
        participantRepository.delete(participant);
    }

    @Override
    @Transactional
    public ParticipantDto checkIn(UUID raceId, UUID participantId) {
        Participant participant = getParticipant(raceId, participantId);
        participant.setCheckedInAt(OffsetDateTime.now());
        if (participant.getStatus() == Participant.ParticipantStatus.REGISTERED ||
            participant.getStatus() == Participant.ParticipantStatus.CONFIRMED) {
            participant.setStatus(Participant.ParticipantStatus.CHECKED_IN);
        }
        Participant saved = participantRepository.save(participant);
        return participantMapper.toDto(saved);
    }

    @Override
    @Transactional
    public ImportJobDto importCsv(UUID raceId, MultipartFile file) {
        Race race = getRace(raceId);
        User user = resolveCurrentUser();

        String originalFilename = file.getOriginalFilename();
        String storageName = UUID.randomUUID() + "-" + originalFilename;
        Path uploadPath = Paths.get(raceTrackerProperties.getStorage().getUploadDir());
        
        try {
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }
            Files.copy(file.getInputStream(), uploadPath.resolve(storageName), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ApiException("Failed to store upload file: " + e.getMessage());
        }

        ImportJob job = ImportJob.builder()
            .race(race)
            .createdBy(user)
            .fileName(originalFilename)
            .status(ImportJob.JobStatus.PENDING)
            .build();

        job = importJobRepository.save(job);
        
        processImportAsync(job.getId(), uploadPath.resolve(storageName).toString());
        
        return importJobMapper.toDto(job);
    }

    @Override
    @Transactional(readOnly = true)
    public ImportJobDto getImportJob(UUID jobId) {
        ImportJob job = importJobRepository.findById(jobId)
            .orElseThrow(() -> new ResourceNotFoundException("Import job not found"));
        return importJobMapper.toDto(job);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ImportJobDto> listImportJobs(UUID raceId) {
        verifyRaceExists(raceId);
        return importJobRepository.findByRaceIdOrderByCreatedAtDesc(raceId)
            .stream()
            .map(importJobMapper::toDto)
            .toList();
    }

    @Async
    public void processImportAsync(UUID jobId, String filePath) {
        ImportJob job = importJobRepository.findById(jobId).orElse(null);
        if (job == null) return;

        job.setStatus(ImportJob.JobStatus.PROCESSING);
        job.setStartedAt(OffsetDateTime.now());
        importJobRepository.save(job);

        Race race = job.getRace();
        int imported = 0;
        int skipped = 0;
        int total = 0;

        try (CSVReader reader = new CSVReader(new InputStreamReader(new FileInputStream(filePath), StandardCharsets.UTF_8))) {
            String[] headers = reader.readNext();
            if (headers != null) {
                Map<String, Integer> index = headerIndex(headers);
                String[] row;
                while ((row = reader.readNext()) != null) {
                    total++;
                    try {
                        Participant participant = new Participant();
                        participant.setRace(race);
                        participant.setFirstName(requiredCell(row, index, "firstName"));
                        participant.setLastName(requiredCell(row, index, "lastName"));
                        participant.setEmail(requiredCell(row, index, "email").toLowerCase(Locale.ROOT));
                        participant.setPhone(cell(row, index, "phone"));
                        participant.setNationality(cell(row, index, "nationality"));
                        participant.setBibNumber(cell(row, index, "bibNumber"));
                        participant.setRfidTag(cell(row, index, "rfidTag"));
                        participant.setRegistrationSource(Optional.ofNullable(cell(row, index, "registrationSource")).orElse("CSV_IMPORT"));
                        participant.setStatus(parseStatusNullable(cell(row, index, "status"), Participant.ParticipantStatus.REGISTERED));
                        participant.setGender(parseGenderNullable(cell(row, index, "gender")));
                        participant.setIsTrackingEnabled(parseBoolean(cell(row, index, "isTrackingEnabled"), true));

                        String dob = cell(row, index, "dateOfBirth");
                        if (dob != null && !dob.isBlank()) {
                            participant.setDateOfBirth(LocalDate.parse(dob));
                        }

                        String categoryId = cell(row, index, "categoryId");
                        if (categoryId != null && !categoryId.isBlank()) {
                            participant.setCategory(resolveCategory(race.getId(), UUID.fromString(categoryId)));
                        }

                        participant.setRegisteredAt(OffsetDateTime.now());
                        participantRepository.save(participant);
                        imported++;
                    } catch (Exception e) {
                        skipped++;
                    }
                    
                    if (total % 10 == 0) {
                        job.setProcessedRows(total);
                        job.setSuccessCount(imported);
                        job.setErrorCount(skipped);
                        importJobRepository.save(job);
                    }
                }
            }
            job.setStatus(ImportJob.JobStatus.COMPLETED);
        } catch (Exception e) {
            job.setStatus(ImportJob.JobStatus.FAILED);
            job.setErrorMessage(e.getMessage());
        } finally {
            job.setTotalRows(total);
            job.setProcessedRows(total);
            job.setSuccessCount(imported);
            job.setErrorCount(skipped);
            job.setCompletedAt(OffsetDateTime.now());
            importJobRepository.save(job);
            
            // Delete temp file
            try { Files.deleteIfExists(Paths.get(filePath)); } catch (IOException ignored) {}
        }
    }

    @Override
    @Transactional(readOnly = true)
    public String exportCsv(UUID raceId) {
        List<Participant> participants = participantRepository.findByRaceIdOrderByRegisteredAtDesc(raceId);
        StringWriter out = new StringWriter();
        out.append("id,firstName,lastName,email,phone,bibNumber,status,gender,nationality,registeredAt\n");

        for (Participant p : participants) {
            out.append(csv(p.getId())).append(',')
                .append(csv(p.getFirstName())).append(',')
                .append(csv(p.getLastName())).append(',')
                .append(csv(p.getEmail())).append(',')
                .append(csv(p.getPhone())).append(',')
                .append(csv(p.getBibNumber())).append(',')
                .append(csv(p.getStatus() != null ? p.getStatus().name() : null)).append(',')
                .append(csv(p.getGender() != null ? p.getGender().name() : null)).append(',')
                .append(csv(p.getNationality())).append(',')
                .append(csv(p.getRegisteredAt()))
                .append('\n');
        }

        return out.toString();
    }

    @Override
    @Transactional
    public RegistrationDto registerForRace(UUID raceId, RaceRegistrationRequest request) {
        Race race = getRace(raceId);
        User user = resolveCurrentUser();

        if (participantRepository.existsByRaceIdAndUserId(raceId, user.getId())) {
            throw new ConflictException("You are already registered for this race");
        }

        RaceCategory category = resolveCategory(raceId, request.getCategoryId());

        Participant participant = new Participant();
        participant.setRace(race);
        participant.setCategory(category);
        participant.setUser(user);
        participant.setFirstName(user.getFirstName());
        participant.setLastName(user.getLastName());
        participant.setEmail(user.getEmail());
        participant.setPhone(user.getPhone());
        participant.setStatus(Participant.ParticipantStatus.REGISTERED);
        participant.setRegistrationSource("PUBLIC_PORTAL");
        participant.setRegisteredAt(OffsetDateTime.now());
        participant.setEmergencyContactName(request.getEmergencyContact().getName());
        participant.setEmergencyContactPhone(request.getEmergencyContact().getPhone());
        participant.setEmergencyContactRelation(request.getEmergencyContact().getRelationship());
        participant.setIsTrackingEnabled(true);

        Participant saved = participantRepository.save(participant);
        return toRegistrationDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RegistrationDto> getMyRegistrations() {
        User user = resolveCurrentUser();
        return participantRepository.findByUserIdOrderByRegisteredAtDesc(user.getId())
            .stream()
            .map(this::toRegistrationDto)
            .toList();
    }

    @Override
    @Transactional
    public void cancelRegistration(UUID registrationId) {
        User user = resolveCurrentUser();
        Participant participant = participantRepository.findById(registrationId)
            .orElseThrow(() -> new ResourceNotFoundException("Registration not found"));

        if (participant.getUser() == null || !user.getId().equals(participant.getUser().getId())) {
            throw new UnauthorizedException("You are not allowed to cancel this registration");
        }

        participant.setStatus(Participant.ParticipantStatus.DNS);
        participantRepository.save(participant);
    }

    private void applyCreateFields(Participant participant, ParticipantCreateRequest request) {
        participant.setCategory(resolveCategoryNullable(participant.getRace().getId(), request.getCategoryId()));
        participant.setFirstName(request.getFirstName().trim());
        participant.setLastName(request.getLastName().trim());
        participant.setEmail(request.getEmail().trim().toLowerCase(Locale.ROOT));
        participant.setPhone(request.getPhone());
        participant.setDateOfBirth(request.getDateOfBirth());
        participant.setGender(parseGenderNullable(request.getGender()));
        participant.setNationality(request.getNationality());
        participant.setBibNumber(request.getBibNumber());
        participant.setRfidTag(request.getRfidTag());
        participant.setEmergencyContactName(request.getEmergencyContactName());
        participant.setEmergencyContactPhone(request.getEmergencyContactPhone());
        participant.setEmergencyContactRelation(request.getEmergencyContactRelation());
        participant.setBloodType(request.getBloodType());
        participant.setMedicalConditions(request.getMedicalConditions());
        participant.setAllergies(request.getAllergies());
        participant.setClubName(request.getClubName());
        participant.setTeamName(request.getTeamName());
        participant.setRegistrationSource(request.getRegistrationSource() != null ? request.getRegistrationSource() : "ADMIN");
        participant.setIsTrackingEnabled(request.getIsTrackingEnabled() != null ? request.getIsTrackingEnabled() : true);
    }

    private void applyUpdateFields(Participant participant, ParticipantUpdateRequest request) {
        if (request.getCategoryId() != null) participant.setCategory(resolveCategory(participant.getRace().getId(), request.getCategoryId()));
        if (request.getFirstName() != null) participant.setFirstName(request.getFirstName());
        if (request.getLastName() != null) participant.setLastName(request.getLastName());
        if (request.getEmail() != null) participant.setEmail(request.getEmail().toLowerCase(Locale.ROOT));
        if (request.getPhone() != null) participant.setPhone(request.getPhone());
        if (request.getDateOfBirth() != null) participant.setDateOfBirth(request.getDateOfBirth());
        if (request.getGender() != null) participant.setGender(parseGenderNullable(request.getGender()));
        if (request.getNationality() != null) participant.setNationality(request.getNationality());
        if (request.getBibNumber() != null) participant.setBibNumber(request.getBibNumber());
        if (request.getRfidTag() != null) participant.setRfidTag(request.getRfidTag());
        if (request.getStatus() != null) participant.setStatus(parseStatus(request.getStatus()));
        if (request.getEmergencyContactName() != null) participant.setEmergencyContactName(request.getEmergencyContactName());
        if (request.getEmergencyContactPhone() != null) participant.setEmergencyContactPhone(request.getEmergencyContactPhone());
        if (request.getEmergencyContactRelation() != null) participant.setEmergencyContactRelation(request.getEmergencyContactRelation());
        if (request.getBloodType() != null) participant.setBloodType(request.getBloodType());
        if (request.getMedicalConditions() != null) participant.setMedicalConditions(request.getMedicalConditions());
        if (request.getAllergies() != null) participant.setAllergies(request.getAllergies());
        if (request.getClubName() != null) participant.setClubName(request.getClubName());
        if (request.getTeamName() != null) participant.setTeamName(request.getTeamName());
        if (request.getRegistrationSource() != null) participant.setRegistrationSource(request.getRegistrationSource());
        if (request.getIsTrackingEnabled() != null) participant.setIsTrackingEnabled(request.getIsTrackingEnabled());
    }

    private RegistrationDto toRegistrationDto(Participant participant) {
        RegistrationDto dto = new RegistrationDto();
        dto.setId(participant.getId());
        dto.setRaceId(participant.getRace() != null ? participant.getRace().getId() : null);
        dto.setUserId(participant.getUser() != null ? participant.getUser().getId() : null);
        dto.setCategoryId(participant.getCategory() != null ? participant.getCategory().getId() : null);
        dto.setBibNumber(participant.getBibNumber());
        dto.setStatus(mapRegistrationStatus(participant.getStatus()));
        dto.setRegistrationDate(participant.getRegisteredAt());

        RegistrationDto.EmergencyContact emergencyContact = new RegistrationDto.EmergencyContact();
        emergencyContact.setName(participant.getEmergencyContactName());
        emergencyContact.setPhone(participant.getEmergencyContactPhone());
        emergencyContact.setRelationship(participant.getEmergencyContactRelation());
        dto.setEmergencyContact(emergencyContact);
        return dto;
    }

    private String mapRegistrationStatus(Participant.ParticipantStatus status) {
        if (status == null) return "pending";
        return switch (status) {
            case CONFIRMED, CHECKED_IN, STARTED -> "confirmed";
            case DNS -> "dns";
            case DNF -> "dnf";
            case FINISHED -> "finished";
            case DISQUALIFIED -> "cancelled";
            case REGISTERED -> "pending";
        };
    }

    private Participant.ParticipantStatus parseStatus(String input) {
        try {
            return Participant.ParticipantStatus.valueOf(input.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            throw new ApiException("Invalid participant status: " + input);
        }
    }

    private Participant.ParticipantStatus parseStatusNullable(String input, Participant.ParticipantStatus fallback) {
        if (input == null || input.isBlank()) {
            return fallback;
        }
        return parseStatus(input);
    }

    private Participant.Gender parseGenderNullable(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        try {
            return Participant.Gender.valueOf(input.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ApiException("Invalid gender: " + input);
        }
    }

    private Participant getParticipant(UUID raceId, UUID participantId) {
        return participantRepository.findByRaceIdAndId(raceId, participantId)
            .orElseThrow(() -> new ResourceNotFoundException("Participant not found"));
    }

    private Race getRace(UUID raceId) {
        return raceRepository.findById(raceId)
            .orElseThrow(() -> new ResourceNotFoundException("Race not found"));
    }

    private void verifyRaceExists(UUID raceId) {
        if (!raceRepository.existsById(raceId)) {
            throw new ResourceNotFoundException("Race not found");
        }
    }

    private RaceCategory resolveCategory(UUID raceId, UUID categoryId) {
        RaceCategory category = raceCategoryRepository.findById(categoryId)
            .orElseThrow(() -> new ResourceNotFoundException("Race category not found"));
        if (category.getRace() == null || !raceId.equals(category.getRace().getId())) {
            throw new ApiException("Category does not belong to race");
        }
        return category;
    }

    private RaceCategory resolveCategoryNullable(UUID raceId, UUID categoryId) {
        if (categoryId == null) return null;
        return resolveCategory(raceId, categoryId);
    }

    private User resolveCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new UnauthorizedException("Unauthorized");
        }
        return userRepository.findByEmailIgnoreCase(authentication.getName())
            .orElseThrow(() -> new UnauthorizedException("Unauthorized"));
    }

    private Map<String, Integer> headerIndex(String[] headers) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            if (headers[i] != null) {
                map.put(headers[i].trim(), i);
            }
        }
        return map;
    }

    private String cell(String[] row, Map<String, Integer> index, String key) {
        Integer i = index.get(key);
        if (i == null || i < 0 || i >= row.length) return null;
        String value = row[i];
        return value == null ? null : value.trim();
    }

    private String requiredCell(String[] row, Map<String, Integer> index, String key) {
        String value = cell(row, index, key);
        if (value == null || value.isBlank()) {
            throw new ApiException("Missing required field: " + key);
        }
        return value;
    }

    private boolean parseBoolean(String input, boolean fallback) {
        if (input == null || input.isBlank()) return fallback;
        return "true".equalsIgnoreCase(input) || "1".equals(input) || "yes".equalsIgnoreCase(input);
    }

    private String csv(Object value) {
        if (value == null) return "";
        String s = String.valueOf(value).replace("\"", "\"\"");
        return '"' + s + '"';
    }
}
