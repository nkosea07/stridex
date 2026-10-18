package com.racetracker.service;

import com.racetracker.dto.participant.*;
import com.racetracker.model.Participant;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface ParticipantService {
    List<ParticipantDto> listByRace(UUID raceId);
    List<ParticipantDto> listByRace(UUID raceId, Participant.ParticipantStatus status);
    ParticipantDto getByRaceAndId(UUID raceId, UUID participantId);
    ParticipantDto create(UUID raceId, ParticipantCreateRequest request);
    ParticipantDto update(UUID raceId, UUID participantId, ParticipantUpdateRequest request);
    void delete(UUID raceId, UUID participantId);
    ParticipantDto checkIn(UUID raceId, UUID participantId);

    ImportJobDto importCsv(UUID raceId, MultipartFile file);
    ImportJobDto getImportJob(UUID raceId, UUID jobId);
    List<ImportJobDto> listImportJobs(UUID raceId);

    String exportCsv(UUID raceId);

    RegistrationDto registerForRace(UUID raceId, RaceRegistrationRequest request);
    List<RegistrationDto> getMyRegistrations();
    void cancelRegistration(UUID registrationId);

    ParticipantDto reviewRegistration(UUID raceId, UUID participantId, RegistrationApprovalRequest request, String actorEmail);
    ParticipantDto verifyPayment(UUID raceId, UUID participantId, PaymentVerifyRequest request, String actorEmail);

    List<RaceRegistrationFieldDto> listRegistrationFields(UUID raceId, boolean activeOnly);
    RaceRegistrationFieldDto createRegistrationField(UUID raceId, RegistrationFieldRequest request);
    RaceRegistrationFieldDto updateRegistrationField(UUID raceId, UUID fieldId, RegistrationFieldRequest request);
    void deleteRegistrationField(UUID raceId, UUID fieldId);

    List<TeamDto> listTeams(UUID raceId);
    TeamDto createTeam(UUID raceId, TeamCreateRequest request);
}
