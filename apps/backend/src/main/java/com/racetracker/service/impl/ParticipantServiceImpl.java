package com.racetracker.service.impl;

import com.opencsv.CSVReader;
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
import com.racetracker.service.ParticipantCredentialService;
import com.racetracker.service.RegistrationEmailService;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.math.BigDecimal;
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

    private static final EnumSet<Participant.ParticipantStatus> OCCUPYING_STATUSES = EnumSet.of(
        Participant.ParticipantStatus.REGISTERED,
        Participant.ParticipantStatus.CONFIRMED,
        Participant.ParticipantStatus.CHECKED_IN,
        Participant.ParticipantStatus.STARTED,
        Participant.ParticipantStatus.FINISHED,
        Participant.ParticipantStatus.PENDING_REVIEW
    );

    private final ParticipantRepository participantRepository;
    private final RaceRepository raceRepository;
    private final RaceCategoryRepository raceCategoryRepository;
    private final UserRepository userRepository;
    private final ImportJobRepository importJobRepository;
    private final ParticipantMapper participantMapper;
    private final ImportJobMapper importJobMapper;
    private final com.racetracker.config.RaceTrackerProperties raceTrackerProperties;
    private final ParticipantCredentialService participantCredentialService;
    private final TeamRepository teamRepository;
    private final RaceRegistrationFieldRepository registrationFieldRepository;
    private final ParticipantRegistrationAnswerRepository registrationAnswerRepository;
    private final RegistrationEmailService registrationEmailService;
    private final com.racetracker.service.NotificationService notificationService;
    private final ParticipantImportProcessor importProcessor;

    public ParticipantServiceImpl(
        ParticipantRepository participantRepository,
        RaceRepository raceRepository,
        RaceCategoryRepository raceCategoryRepository,
        UserRepository userRepository,
        ImportJobRepository importJobRepository,
        ParticipantMapper participantMapper,
        ImportJobMapper importJobMapper,
        com.racetracker.config.RaceTrackerProperties raceTrackerProperties,
        ParticipantCredentialService participantCredentialService,
        TeamRepository teamRepository,
        RaceRegistrationFieldRepository registrationFieldRepository,
        ParticipantRegistrationAnswerRepository registrationAnswerRepository,
        RegistrationEmailService registrationEmailService,
        com.racetracker.service.NotificationService notificationService,
        ParticipantImportProcessor importProcessor
    ) {
        this.participantRepository = participantRepository;
        this.raceRepository = raceRepository;
        this.raceCategoryRepository = raceCategoryRepository;
        this.userRepository = userRepository;
        this.importJobRepository = importJobRepository;
        this.participantMapper = participantMapper;
        this.importJobMapper = importJobMapper;
        this.raceTrackerProperties = raceTrackerProperties;
        this.participantCredentialService = participantCredentialService;
        this.teamRepository = teamRepository;
        this.registrationFieldRepository = registrationFieldRepository;
        this.registrationAnswerRepository = registrationAnswerRepository;
        this.registrationEmailService = registrationEmailService;
        this.notificationService = notificationService;
        this.importProcessor = importProcessor;
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
    public List<ParticipantDto> listByRace(UUID raceId, Participant.ParticipantStatus status) {
        if (status == null) {
            return listByRace(raceId);
        }
        verifyRaceExists(raceId);
        return participantRepository.findByRaceIdAndStatusOrderByRegisteredAtAsc(raceId, status)
            .stream()
            .map(participantMapper::toDto)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ParticipantDto getByRaceAndId(UUID raceId, UUID participantId) {
        Participant participant = getParticipant(raceId, participantId);
        ParticipantDto dto = participantMapper.toDto(participant);
        dto.setAnswers(loadAnswers(participantId));
        return dto;
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
        if (participant.getPaymentStatus() == null) {
            participant.setPaymentStatus(defaultPaymentStatus(race));
        }

        Participant saved = participantRepository.save(participant);
        participantCredentialService.syncParticipantCredentials(saved, request.getRfidTag(), request.getCredentials());
        return participantMapper.toDto(saved);
    }

    @Override
    @Transactional
    public ParticipantDto update(UUID raceId, UUID participantId, ParticipantUpdateRequest request) {
        Participant participant = getParticipant(raceId, participantId);
        applyUpdateFields(participant, request);

        Participant saved = participantRepository.save(participant);
        participantCredentialService.syncParticipantCredentials(saved, request.getRfidTag(), request.getCredentials());
        return participantMapper.toDto(saved);
    }

    @Override
    @Transactional
    public void delete(UUID raceId, UUID participantId) {
        Participant participant = getParticipant(raceId, participantId);
        registrationAnswerRepository.deleteByParticipantId(participantId);
        participantRepository.delete(participant);
    }

    @Override
    @Transactional
    public ParticipantDto checkIn(UUID raceId, UUID participantId) {
        Participant participant = getParticipant(raceId, participantId);
        if (participant.getStatus() != Participant.ParticipantStatus.REGISTERED
            && participant.getStatus() != Participant.ParticipantStatus.CONFIRMED) {
            throw new ApiException("Participant is not eligible for check-in");
        }
        participant.setCheckedInAt(OffsetDateTime.now());
        participant.setStatus(Participant.ParticipantStatus.CHECKED_IN);
        Participant saved = participantRepository.save(participant);
        notifyParticipant(saved, com.racetracker.model.Notification.NotificationType.RACE_UPDATE,
            "registration.checked_in", "Check-in confirmed", "You are checked in for " + raceName(saved) + ".");
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
            .sourcePath(uploadPath.resolve(storageName).toString())
            .status(ImportJob.JobStatus.PENDING)
            .build();

        job = importJobRepository.save(job);

        importProcessor.process(job.getId());

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
        Race race = raceRepository.findByIdForUpdate(raceId)
            .orElseThrow(() -> new ResourceNotFoundException("Race not found"));
        User user = resolveCurrentUser();

        if (participantRepository.existsByRaceIdAndUserId(raceId, user.getId())) {
            throw new ConflictException("You are already registered for this race");
        }

        validateRegistrationOpen(race);

        RaceCategory category = resolveCategory(raceId, request.getCategoryId());

        if (Boolean.TRUE.equals(race.getRequireWaiver())
            && !Boolean.TRUE.equals(request.getWaiverAccepted())) {
            throw new ApiException("You must accept the race waiver before registering");
        }

        Map<UUID, String> answers = validateAnswers(raceId, request.getAnswers());

        boolean full = isRaceFull(race);
        Participant.ParticipantStatus status;
        Integer waitlistPosition = null;
        if (full) {
            if (!Boolean.TRUE.equals(race.getWaitlistEnabled())) {
                throw new ConflictException("This race is full");
            }
            status = Participant.ParticipantStatus.WAITLISTED;
            waitlistPosition = nextWaitlistPosition(raceId);
        } else if (Boolean.TRUE.equals(race.getRequireApproval())) {
            status = Participant.ParticipantStatus.PENDING_REVIEW;
        } else {
            status = Participant.ParticipantStatus.REGISTERED;
        }

        Participant participant = new Participant();
        participant.setRace(race);
        participant.setCategory(category);
        participant.setUser(user);
        participant.setFirstName(user.getFirstName());
        participant.setLastName(user.getLastName());
        participant.setEmail(user.getEmail());
        participant.setPhone(user.getPhone());
        participant.setStatus(status);
        participant.setRegistrationSource("PUBLIC_PORTAL");
        participant.setRegisteredAt(OffsetDateTime.now());
        participant.setEmergencyContactName(request.getEmergencyContact().getName());
        participant.setEmergencyContactPhone(request.getEmergencyContact().getPhone());
        participant.setEmergencyContactRelation(request.getEmergencyContact().getRelationship());
        participant.setIsTrackingEnabled(true);
        participant.setShirtSize(request.getShirtSize());
        participant.setWaitlistPosition(waitlistPosition);
        participant.setPaymentStatus(defaultPaymentStatus(race));
        if (request.getClubName() != null && !request.getClubName().isBlank()) {
            participant.setClubName(request.getClubName().trim());
        }
        if (Boolean.TRUE.equals(race.getRequireWaiver())) {
            participant.setWaiverAcceptedAt(OffsetDateTime.now());
            participant.setWaiverVersion(race.getWaiverVersion());
        }
        participant.setTeam(resolveRegistrationTeam(race, user, request));

        Participant saved = participantRepository.save(participant);
        saveAnswers(saved, answers);

        RegistrationDto dto = toRegistrationDto(saved);
        sendRegistrationEmailAfterCommit(saved);
        return dto;
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

        if (participant.getStatus() == Participant.ParticipantStatus.STARTED
            || participant.getStatus() == Participant.ParticipantStatus.FINISHED
            || participant.getStatus() == Participant.ParticipantStatus.CHECKED_IN) {
            throw new ApiException("Registration can no longer be cancelled");
        }

        participant.setStatus(Participant.ParticipantStatus.DNS);
        participant.setWaitlistPosition(null);
        participantRepository.save(participant);
    }

    @Override
    @Transactional
    public ParticipantDto reviewRegistration(UUID raceId, UUID participantId,
                                             RegistrationApprovalRequest request, String actorEmail) {
        if (!raceRepository.existsById(raceId)) {
            throw new ResourceNotFoundException("Race not found");
        }
        raceRepository.findByIdForUpdate(raceId)
            .orElseThrow(() -> new ResourceNotFoundException("Race not found"));
        Participant participant = participantRepository.findByRaceIdAndIdForUpdate(raceId, participantId)
            .orElseThrow(() -> new ResourceNotFoundException("Participant not found"));
        Race race = participant.getRace();
        ensureReviewable(participant);
        UUID actorId = resolveUserId(actorEmail);
        String decision = request.getDecision() == null ? "" : request.getDecision().trim().toUpperCase(Locale.ROOT);

        switch (decision) {
            case "APPROVE", "APPROVED", "CONFIRM", "CONFIRMED" -> {
                boolean alreadyOccupying = OCCUPYING_STATUSES.contains(participant.getStatus());
                if (!alreadyOccupying && isRaceFull(race) && !Boolean.TRUE.equals(request.getForce())) {
                    throw new ConflictException("Race is full; waitlist or increase capacity before approving");
                }
                if (race.getRegistrationFee() != null && race.getRegistrationFee().compareTo(BigDecimal.ZERO) > 0
                    && !Boolean.TRUE.equals(request.getForce())
                    && participant.getPaymentStatus() != Participant.PaymentStatus.PAID
                    && participant.getPaymentStatus() != Participant.PaymentStatus.WAIVED) {
                    throw new ApiException("Payment must be verified before approval");
                }
                participant.setStatus(Participant.ParticipantStatus.CONFIRMED);
                participant.setApprovedAt(OffsetDateTime.now());
                participant.setApprovedBy(actorId);
                participant.setRejectionReason(null);
                participant.setWaitlistPosition(null);
            }
            case "REJECT", "REJECTED" -> {
                participant.setStatus(Participant.ParticipantStatus.REJECTED);
                participant.setApprovedBy(actorId);
                participant.setApprovedAt(null);
                participant.setRejectionReason(request.getReason());
                participant.setWaitlistPosition(null);
            }
            case "WAITLIST", "WAITLISTED" -> {
                boolean alreadyWaitlisted = participant.getStatus() == Participant.ParticipantStatus.WAITLISTED
                    && participant.getWaitlistPosition() != null;
                participant.setStatus(Participant.ParticipantStatus.WAITLISTED);
                participant.setApprovedBy(actorId);
                participant.setApprovedAt(null);
                if (!alreadyWaitlisted) {
                    participant.setWaitlistPosition(nextWaitlistPosition(raceId));
                }
            }
            default -> throw new ApiException("Unknown registration decision: " + request.getDecision());
        }

        Participant saved = participantRepository.save(participant);
        sendReviewEmailAfterCommit(saved);
        return participantMapper.toDto(saved);
    }

    @Override
    @Transactional
    public ParticipantDto verifyPayment(UUID raceId, UUID participantId,
                                        PaymentVerifyRequest request, String actorEmail) {
        Participant participant = participantRepository.findByRaceIdAndIdForUpdate(raceId, participantId)
            .orElseThrow(() -> new ResourceNotFoundException("Participant not found"));
        Participant.PaymentStatus status;
        try {
            status = Participant.PaymentStatus.valueOf(request.getStatus().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ApiException("Invalid payment status: " + request.getStatus());
        }

        participant.setPaymentStatus(status);
        participant.setPaymentReference(request.getReference());
        if (request.getAmount() != null) {
            participant.setPaymentAmount(request.getAmount());
        }
        participant.setPaymentVerifiedBy(resolveUserId(actorEmail));
        participant.setPaymentVerifiedAt(OffsetDateTime.now());

        Participant saved = participantRepository.save(participant);
        return participantMapper.toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RaceRegistrationFieldDto> listRegistrationFields(UUID raceId, boolean activeOnly) {
        Race race = raceRepository.findById(raceId)
            .orElseThrow(() -> new ResourceNotFoundException("Race not found"));
        if (activeOnly && race.getStatus() == Race.RaceStatus.DRAFT) {
            throw new ResourceNotFoundException("Race not found");
        }
        List<RaceRegistrationField> fields = activeOnly
            ? registrationFieldRepository.findByRaceIdAndActiveTrueOrderByDisplayOrderAsc(raceId)
            : registrationFieldRepository.findByRaceIdOrderByDisplayOrderAsc(raceId);
        return fields.stream().map(this::toFieldDto).toList();
    }

    @Override
    @Transactional
    public RaceRegistrationFieldDto createRegistrationField(UUID raceId, RegistrationFieldRequest request) {
        Race race = getRace(raceId);
        String fieldKey = deriveFieldKey(request.getFieldKey(), request.getLabel());
        registrationFieldRepository.findByRaceIdAndFieldKey(raceId, fieldKey).ifPresent(existing -> {
            throw new ConflictException("A registration field with key '" + fieldKey + "' already exists");
        });

        RaceRegistrationField.FieldType type = parseFieldType(request.getFieldType());
        if (type == RaceRegistrationField.FieldType.SELECT
            && (request.getOptions() == null || request.getOptions().isEmpty())) {
            throw new ApiException("SELECT fields require at least one option");
        }

        RaceRegistrationField field = RaceRegistrationField.builder()
            .race(race)
            .fieldKey(fieldKey)
            .label(request.getLabel().trim())
            .fieldType(type)
            .required(Boolean.TRUE.equals(request.getRequired()))
            .options(request.getOptions() != null ? request.getOptions() : new ArrayList<>())
            .displayOrder(request.getDisplayOrder() != null ? request.getDisplayOrder() : 0)
            .active(request.getActive() == null || Boolean.TRUE.equals(request.getActive()))
            .build();

        return toFieldDto(registrationFieldRepository.save(field));
    }

    @Override
    @Transactional
    public RaceRegistrationFieldDto updateRegistrationField(UUID raceId, UUID fieldId, RegistrationFieldRequest request) {
        RaceRegistrationField field = registrationFieldRepository.findByRaceIdAndId(raceId, fieldId)
            .orElseThrow(() -> new ResourceNotFoundException("Registration field not found"));

        RaceRegistrationField.FieldType type = parseFieldType(
            request.getFieldType() != null ? request.getFieldType() : field.getFieldType().name());
        if (type == RaceRegistrationField.FieldType.SELECT
            && (request.getOptions() == null || request.getOptions().isEmpty())) {
            throw new ApiException("SELECT fields require at least one option");
        }

        field.setLabel(request.getLabel().trim());
        field.setFieldType(type);
        field.setRequired(Boolean.TRUE.equals(request.getRequired()));
        if (request.getOptions() != null) {
            field.setOptions(request.getOptions());
        }
        if (request.getDisplayOrder() != null) {
            field.setDisplayOrder(request.getDisplayOrder());
        }
        if (request.getActive() != null) {
            field.setActive(request.getActive());
        }

        return toFieldDto(registrationFieldRepository.save(field));
    }

    @Override
    @Transactional
    public void deleteRegistrationField(UUID raceId, UUID fieldId) {
        RaceRegistrationField field = registrationFieldRepository.findByRaceIdAndId(raceId, fieldId)
            .orElseThrow(() -> new ResourceNotFoundException("Registration field not found"));
        registrationFieldRepository.delete(field);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TeamDto> listTeams(UUID raceId) {
        verifyRaceExists(raceId);
        return teamRepository.findByRaceIdOrderByNameAsc(raceId)
            .stream()
            .map(this::toTeamDto)
            .toList();
    }

    @Override
    @Transactional
    public TeamDto createTeam(UUID raceId, TeamCreateRequest request) {
        Race race = getRace(raceId);
        User user = resolveCurrentUser();
        String name = request.getName().trim();
        teamRepository.findByRaceIdAndNameIgnoreCase(raceId, name).ifPresent(existing -> {
            throw new ConflictException("A team named '" + name + "' already exists for this race");
        });

        Team team = Team.builder()
            .race(race)
            .name(name)
            .clubName(request.getClubName())
            .captainUser(user)
            .status(Team.TeamStatus.OPEN)
            .build();

        return toTeamDto(teamRepository.save(team));
    }

    // ------------------------------------------------------------------
    // Registration helpers
    // ------------------------------------------------------------------

    private void validateRegistrationOpen(Race race) {
        if (!Boolean.TRUE.equals(race.getIsPublicRegistration())) {
            throw new ApiException("Public registration is not available for this race");
        }
        if (race.getStatus() == Race.RaceStatus.CANCELLED
            || race.getStatus() == Race.RaceStatus.COMPLETED
            || race.getStatus() == Race.RaceStatus.IN_PROGRESS) {
            throw new ApiException("Registration is not open for this race");
        }

        OffsetDateTime now = OffsetDateTime.now();
        if (race.getRegistrationOpens() != null && now.isBefore(race.getRegistrationOpens())) {
            throw new ApiException("Registration has not opened yet");
        }
        if (race.getRegistrationCloses() != null && now.isAfter(race.getRegistrationCloses())) {
            throw new ApiException("Registration has closed for this race");
        }
    }

    private boolean isRaceFull(Race race) {
        if (race.getMaxParticipants() == null) {
            return false;
        }
        long occupying = participantRepository.countByRaceIdAndStatusIn(race.getId(), OCCUPYING_STATUSES);
        return occupying >= race.getMaxParticipants();
    }

    private void ensureReviewable(Participant participant) {
        Participant.ParticipantStatus status = participant.getStatus();
        if (status == Participant.ParticipantStatus.CHECKED_IN
            || status == Participant.ParticipantStatus.STARTED
            || status == Participant.ParticipantStatus.FINISHED
            || status == Participant.ParticipantStatus.DNF
            || status == Participant.ParticipantStatus.DNS
            || status == Participant.ParticipantStatus.DISQUALIFIED) {
            throw new ApiException("Registration cannot be reviewed in its current state");
        }
    }

    private int nextWaitlistPosition(UUID raceId) {
        Integer max = participantRepository.maxWaitlistPosition(raceId, Participant.ParticipantStatus.WAITLISTED);
        return (max == null ? 0 : max) + 1;
    }

    private Participant.PaymentStatus defaultPaymentStatus(Race race) {
        if (race.getRegistrationFee() == null || race.getRegistrationFee().compareTo(BigDecimal.ZERO) <= 0) {
            return Participant.PaymentStatus.WAIVED;
        }
        return Participant.PaymentStatus.UNPAID;
    }

    private Map<UUID, String> validateAnswers(UUID raceId, List<RegistrationAnswerRequest> requests) {
        List<RaceRegistrationField> fields = registrationFieldRepository.findByRaceIdAndActiveTrueOrderByDisplayOrderAsc(raceId);
        Map<UUID, RaceRegistrationField> byId = new HashMap<>();
        for (RaceRegistrationField field : fields) {
            byId.put(field.getId(), field);
        }

        Map<UUID, String> provided = new HashMap<>();
        if (requests != null) {
            for (RegistrationAnswerRequest answer : requests) {
                if (answer == null || answer.getFieldId() == null) {
                    continue;
                }
                RaceRegistrationField field = byId.get(answer.getFieldId());
                if (field == null) {
                    throw new ApiException("Unknown or inactive registration field: " + answer.getFieldId());
                }
                provided.put(field.getId(), normalizeAnswer(field, answer.getValue()));
            }
        }

        for (RaceRegistrationField field : fields) {
            boolean required = Boolean.TRUE.equals(field.getRequired());
            String value = provided.get(field.getId());
            boolean missing = value == null || value.isBlank();
            if (required && missing) {
                throw new ApiException("Missing required registration field: " + field.getLabel());
            }
            if (required && field.getFieldType() == RaceRegistrationField.FieldType.CHECKBOX
                && !"true".equalsIgnoreCase(value)) {
                throw new ApiException("Field '" + field.getLabel() + "' must be accepted");
            }
        }
        return provided;
    }

    private String normalizeAnswer(RaceRegistrationField field, String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        switch (field.getFieldType()) {
            case NUMBER -> {
                try {
                    Double.parseDouble(value);
                } catch (NumberFormatException ex) {
                    throw new ApiException("Field '" + field.getLabel() + "' must be a number");
                }
            }
            case CHECKBOX -> {
                if (!value.isEmpty() && !"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)
                    && !"1".equals(value) && !"0".equals(value) && !"yes".equalsIgnoreCase(value) && !"no".equalsIgnoreCase(value)) {
                    throw new ApiException("Field '" + field.getLabel() + "' must be a yes/no value");
                }
                value = ("true".equalsIgnoreCase(value) || "1".equals(value) || "yes".equalsIgnoreCase(value)) ? "true" : "false";
            }
            case SELECT -> {
                if (field.getOptions() != null && !field.getOptions().isEmpty() && !field.getOptions().contains(value)) {
                    throw new ApiException("Invalid option for field '" + field.getLabel() + "'");
                }
            }
            default -> {
                // TEXT requires no extra validation.
            }
        }
        return value;
    }

    private void saveAnswers(Participant participant, Map<UUID, String> answers) {
        if (answers == null || answers.isEmpty()) {
            return;
        }
        List<ParticipantRegistrationAnswer> entities = new ArrayList<>();
        for (Map.Entry<UUID, String> entry : answers.entrySet()) {
            RaceRegistrationField field = registrationFieldRepository.findById(entry.getKey()).orElse(null);
            if (field == null) {
                continue;
            }
            entities.add(ParticipantRegistrationAnswer.builder()
                .participant(participant)
                .field(field)
                .value(entry.getValue())
                .build());
        }
        if (!entities.isEmpty()) {
            registrationAnswerRepository.saveAll(entities);
        }
    }

    private Team resolveRegistrationTeam(Race race, User user, RaceRegistrationRequest request) {
        if (request.getTeamId() != null) {
            Team team = teamRepository.findByRaceIdAndId(race.getId(), request.getTeamId())
                .orElseThrow(() -> new ResourceNotFoundException("Team not found"));
            if (team.getStatus() == Team.TeamStatus.LOCKED) {
                throw new ApiException("This team is locked and no longer accepting registrations");
            }
            return team;
        }
        if (request.getTeamName() == null || request.getTeamName().isBlank()) {
            return null;
        }
        String name = request.getTeamName().trim();
        return teamRepository.findByRaceIdAndNameIgnoreCase(race.getId(), name)
            .orElseGet(() -> teamRepository.save(Team.builder()
                .race(race)
                .name(name)
                .clubName(request.getClubName())
                .captainUser(user)
                .status(Team.TeamStatus.OPEN)
                .build()));
    }

    private void sendRegistrationEmailAfterCommit(Participant participant) {
        org.springframework.transaction.support.TransactionSynchronization sync =
            new org.springframework.transaction.support.TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    registrationEmailService.sendRegistrationReceived(participant);
                }
            };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(sync);
        } else {
            registrationEmailService.sendRegistrationReceived(participant);
        }
    }

    private void sendReviewEmailAfterCommit(Participant participant) {
        Participant.ParticipantStatus status = participant.getStatus();
        Runnable action = switch (status) {
            case CONFIRMED, CHECKED_IN -> () -> {
                registrationEmailService.sendRegistrationApproved(participant);
                notifyParticipant(participant, com.racetracker.model.Notification.NotificationType.RACE_UPDATE,
                    "registration.confirmed", "Registration confirmed", "Your registration for " + raceName(participant) + " is confirmed.");
            };
            case WAITLISTED -> () -> {
                registrationEmailService.sendRegistrationWaitlisted(participant);
                notifyParticipant(participant, com.racetracker.model.Notification.NotificationType.RACE_UPDATE,
                    "registration.waitlisted", "Added to waitlist", "You are on the waitlist for " + raceName(participant) + ".");
            };
            case REJECTED -> () -> {
                registrationEmailService.sendRegistrationRejected(participant);
                notifyParticipant(participant, com.racetracker.model.Notification.NotificationType.ALERT,
                    "registration.rejected", "Registration update", "Your registration for " + raceName(participant) + " was not approved.");
            };
            default -> null;
        };
        if (action == null) {
            return;
        }
        TransactionSynchronization sync = new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(sync);
        } else {
            action.run();
        }
    }

    private void notifyParticipant(Participant participant, com.racetracker.model.Notification.NotificationType type,
                                   String templateCode, String title, String message) {
        if (participant.getUser() == null) {
            return;
        }
        try {
            notificationService.notifyTemplated(participant.getUser().getId(), type, templateCode, title, message,
                java.util.Map.of("participantId", participant.getId().toString()));
        } catch (RuntimeException ex) {
            // Notification delivery must never break registration flows.
        }
    }

    private String raceName(Participant participant) {
        return participant.getRace() != null && participant.getRace().getName() != null
            ? participant.getRace().getName() : "your race";
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
        if (request.getStatus() != null) {
            Participant.ParticipantStatus newStatus = parseStatus(request.getStatus());
            participant.setStatus(newStatus);
            if (newStatus != Participant.ParticipantStatus.WAITLISTED) {
                participant.setWaitlistPosition(null);
            }
        }
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
        dto.setShirtSize(participant.getShirtSize());
        dto.setPaymentStatus(participant.getPaymentStatus() != null ? participant.getPaymentStatus().name() : null);
        dto.setPaymentAmount(participant.getPaymentAmount());
        dto.setRejectionReason(participant.getRejectionReason());
        dto.setWaitlistPosition(participant.getWaitlistPosition());
        if (participant.getTeam() != null) {
            dto.setTeamId(participant.getTeam().getId());
            dto.setTeamName(participant.getTeam().getName());
        }

        RegistrationDto.EmergencyContact emergencyContact = new RegistrationDto.EmergencyContact();
        emergencyContact.setName(participant.getEmergencyContactName());
        emergencyContact.setPhone(participant.getEmergencyContactPhone());
        emergencyContact.setRelationship(participant.getEmergencyContactRelation());
        dto.setEmergencyContact(emergencyContact);
        return dto;
    }

    private List<RegistrationAnswerDto> loadAnswers(UUID participantId) {
        return registrationAnswerRepository.findByParticipantId(participantId).stream()
            .map(answer -> {
                RegistrationAnswerDto dto = new RegistrationAnswerDto();
                dto.setFieldId(answer.getField() != null ? answer.getField().getId() : null);
                dto.setFieldKey(answer.getField() != null ? answer.getField().getFieldKey() : null);
                dto.setLabel(answer.getField() != null ? answer.getField().getLabel() : null);
                dto.setValue(answer.getValue());
                return dto;
            })
            .toList();
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

    private TeamDto toTeamDto(Team team) {
        TeamDto dto = new TeamDto();
        dto.setId(team.getId());
        dto.setRaceId(team.getRace() != null ? team.getRace().getId() : null);
        dto.setName(team.getName());
        dto.setClubName(team.getClubName());
        dto.setStatus(team.getStatus() != null ? team.getStatus().name() : null);
        dto.setCaptainUserId(team.getCaptainUser() != null ? team.getCaptainUser().getId() : null);
        if (team.getRace() != null) {
            dto.setMemberCount(participantRepository.countByRaceIdAndTeamId(team.getRace().getId(), team.getId()));
        }
        return dto;
    }

    private RaceRegistrationField.FieldType parseFieldType(String input) {
        if (input == null || input.isBlank()) {
            return RaceRegistrationField.FieldType.TEXT;
        }
        try {
            return RaceRegistrationField.FieldType.valueOf(input.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ApiException("Invalid field type: " + input);
        }
    }

    private String deriveFieldKey(String provided, String label) {
        String base = (provided != null && !provided.isBlank()) ? provided : label;
        String key = base.trim().toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]+", "_")
            .replaceAll("^_+|_+$", "");
        if (key.isBlank()) {
            key = "field_" + UUID.randomUUID().toString().substring(0, 8);
        }
        return key.length() > 80 ? key.substring(0, 80) : key;
    }

    private String mapRegistrationStatus(Participant.ParticipantStatus status) {
        if (status == null) return "pending";
        return switch (status) {
            case CONFIRMED, CHECKED_IN, STARTED -> "confirmed";
            case PENDING_REVIEW -> "pending_review";
            case WAITLISTED -> "waitlisted";
            case REJECTED -> "rejected";
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

    private UUID resolveUserId(String email) {
        if (email == null) {
            return null;
        }
        return userRepository.findByEmailIgnoreCase(email).map(User::getId).orElse(null);
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
