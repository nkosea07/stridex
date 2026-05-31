package com.racetracker.service;

import com.racetracker.dto.participant.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface ParticipantService {
    List<ParticipantDto> listByRace(UUID raceId);
    ParticipantDto getByRaceAndId(UUID raceId, UUID participantId);
    ParticipantDto create(UUID raceId, ParticipantCreateRequest request);
    ParticipantDto update(UUID raceId, UUID participantId, ParticipantUpdateRequest request);
    void delete(UUID raceId, UUID participantId);
    ParticipantDto checkIn(UUID raceId, UUID participantId);

    ImportJobDto importCsv(UUID raceId, MultipartFile file);
    ImportJobDto getImportJob(UUID jobId);
    List<ImportJobDto> listImportJobs(UUID raceId);

    String exportCsv(UUID raceId);

    RegistrationDto registerForRace(UUID raceId, RaceRegistrationRequest request);
    List<RegistrationDto> getMyRegistrations();
    void cancelRegistration(UUID registrationId);
}
