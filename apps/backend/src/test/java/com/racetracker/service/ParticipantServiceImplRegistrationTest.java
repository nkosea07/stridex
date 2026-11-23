package com.racetracker.service;

import com.racetracker.config.RaceTrackerProperties;
import com.racetracker.dto.participant.*;
import com.racetracker.exception.ApiException;
import com.racetracker.exception.ConflictException;
import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.mapper.ImportJobMapper;
import com.racetracker.mapper.ParticipantMapper;
import com.racetracker.model.Participant;
import com.racetracker.model.ParticipantRegistrationAnswer;
import com.racetracker.model.Race;
import com.racetracker.model.RaceCategory;
import com.racetracker.model.RaceRegistrationField;
import com.racetracker.model.User;
import com.racetracker.model.UserCapabilityType;
import com.racetracker.repository.*;
import com.racetracker.service.impl.ParticipantServiceImpl;
import com.racetracker.service.impl.ParticipantImportProcessor;
import com.racetracker.security.CapabilityAuthorizationService;
import com.racetracker.security.RaceAccessGuard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParticipantServiceImplRegistrationTest {

    @Mock com.racetracker.service.impl.ImportSourceStore importSourceStore;
    @Mock ParticipantRepository participantRepository;
    @Mock RaceRepository raceRepository;
    @Mock RaceCategoryRepository raceCategoryRepository;
    @Mock UserRepository userRepository;
    @Mock ImportJobRepository importJobRepository;
    @Mock ParticipantMapper participantMapper;
    @Mock ImportJobMapper importJobMapper;
    @Mock ParticipantCredentialService participantCredentialService;
    @Mock TeamRepository teamRepository;
    @Mock RaceRegistrationFieldRepository registrationFieldRepository;
    @Mock ParticipantRegistrationAnswerRepository registrationAnswerRepository;
    @Mock RegistrationEmailService registrationEmailService;
    @Mock NotificationService notificationService;
    @Mock ParticipantImportProcessor importProcessor;
    @Mock CapabilityAuthorizationService capabilityAuthorizationService;
    @Mock RaceAccessGuard raceAccessGuard;
    @Mock com.racetracker.service.impl.ParticipantIdentityService participantIdentityService;

    private ParticipantServiceImpl service;
    private User user;
    private Race race;
    private RaceCategory category;
    private final UUID raceId = UUID.randomUUID();
    private final UUID categoryId = UUID.randomUUID();
    private final String email = "athlete@example.com";

    private final com.racetracker.service.ParticipantChangeControl participantChangeControl =
        org.mockito.Mockito.mock(com.racetracker.service.ParticipantChangeControl.class);

    @BeforeEach
    void setUp() {
        service = new ParticipantServiceImpl(
            participantRepository, raceRepository, raceCategoryRepository, userRepository,
            importJobRepository, participantMapper, importJobMapper, new RaceTrackerProperties(),
            participantCredentialService, teamRepository, registrationFieldRepository,
            registrationAnswerRepository, registrationEmailService, notificationService, importProcessor,
            capabilityAuthorizationService, raceAccessGuard, participantIdentityService, importSourceStore,
            participantChangeControl
        );

        user = User.builder()
            .id(UUID.randomUUID())
            .firstName("Ada")
            .lastName("Runner")
            .email(email)
            .phone("0710000000")
            .build();

        race = new Race();
        race.setId(raceId);
        race.setName("Test Marathon");
        race.setStatus(Race.RaceStatus.REGISTRATION_OPEN);
        race.setIsPublicRegistration(true);
        race.setRequireApproval(false);
        race.setWaitlistEnabled(false);
        race.setRequireWaiver(false);
        race.setRegistrationFee(BigDecimal.ZERO);
        race.setMaxParticipants(100);

        category = RaceCategory.builder().id(categoryId).race(race).name("Open").build();

        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(email, null, List.of()));
        lenient().when(userRepository.findByEmailIgnoreCase(anyString())).thenReturn(Optional.of(user));
        lenient().when(raceRepository.findByIdForUpdate(raceId)).thenReturn(Optional.of(race));
        lenient().when(raceRepository.existsById(raceId)).thenReturn(true);
        lenient().when(participantRepository.existsByRaceIdAndUserId(raceId, user.getId())).thenReturn(false);
        lenient().when(raceCategoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
        lenient().when(registrationFieldRepository.findByRaceIdAndActiveTrueOrderByDisplayOrderAsc(raceId)).thenReturn(List.of());
        lenient().when(participantRepository.save(any(Participant.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private RaceRegistrationRequest request() {
        RaceRegistrationRequest request = new RaceRegistrationRequest();
        request.setCategoryId(categoryId);
        RaceRegistrationRequest.EmergencyContact contact = new RaceRegistrationRequest.EmergencyContact();
        contact.setName("Next of Kin");
        contact.setPhone("0700000000");
        contact.setRelationship("Sibling");
        request.setEmergencyContact(contact);
        return request;
    }

    @Test
    void registrationRejectedBeforeWindowOpens() {
        race.setRegistrationOpens(OffsetDateTime.now().plusDays(1));

        assertThatThrownBy(() -> service.registerForRace(raceId, request()))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("not opened");
    }

    @Test
    void spectatorOnlyUserCannotRegisterForRace() {
        doThrow(new AccessDeniedException("Missing capability"))
            .when(capabilityAuthorizationService).require(UserCapabilityType.ATHLETE);

        assertThatThrownBy(() -> service.registerForRace(raceId, request()))
            .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(raceRepository, participantRepository, raceCategoryRepository);
    }

    @Test
    void cancellationRequiresAthleteCapability() {
        UUID registrationId = UUID.randomUUID();
        doThrow(new AccessDeniedException("Missing capability"))
            .when(capabilityAuthorizationService).require(UserCapabilityType.ATHLETE);

        assertThatThrownBy(() -> service.cancelRegistration(registrationId))
            .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(participantRepository);
    }

    @Test
    void registrationRejectedAfterWindowCloses() {
        race.setRegistrationCloses(OffsetDateTime.now().minusDays(1));

        assertThatThrownBy(() -> service.registerForRace(raceId, request()))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("closed");
    }

    @Test
    void importJobLookupIsScopedToRace() {
        UUID jobId = UUID.randomUUID();
        when(importJobRepository.findByRaceIdAndId(raceId, jobId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getImportJob(raceId, jobId))
            .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(importJobMapper);
    }

    @Test
    void fullRaceWithoutWaitlistIsRejected() {
        race.setMaxParticipants(10);
        when(participantRepository.countByRaceIdAndStatusIn(eq(raceId), any())).thenReturn(10L);

        assertThatThrownBy(() -> service.registerForRace(raceId, request()))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("full");
    }

    @Test
    void fullRaceWithWaitlistAssignsNextWaitlistPosition() {
        race.setMaxParticipants(10);
        race.setWaitlistEnabled(true);
        when(participantRepository.countByRaceIdAndStatusIn(eq(raceId), any())).thenReturn(10L);
        when(participantRepository.maxWaitlistPosition(raceId, Participant.ParticipantStatus.WAITLISTED)).thenReturn(5);

        RegistrationDto dto = service.registerForRace(raceId, request());

        assertThat(dto.getStatus()).isEqualTo("waitlisted");
        assertThat(dto.getWaitlistPosition()).isEqualTo(6);
    }

    @Test
    void requiredCheckboxMustBeAccepted() {
        RaceRegistrationField field = RaceRegistrationField.builder()
            .id(UUID.randomUUID())
            .race(race)
            .fieldKey("consent")
            .label("Consent")
            .fieldType(RaceRegistrationField.FieldType.CHECKBOX)
            .required(true)
            .active(true)
            .build();
        when(registrationFieldRepository.findByRaceIdAndActiveTrueOrderByDisplayOrderAsc(raceId))
            .thenReturn(List.of(field));

        RegistrationAnswerRequest answer = new RegistrationAnswerRequest();
        answer.setFieldId(field.getId());
        answer.setValue("false");
        RaceRegistrationRequest request = request();
        request.setAnswers(List.of(answer));

        assertThatThrownBy(() -> service.registerForRace(raceId, request))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("must be accepted");
    }

    @Test
    void getByRaceAndIdPopulatesRegistrationAnswers() {
        Participant participant = new Participant();
        participant.setId(UUID.randomUUID());
        participant.setRace(race);
        when(participantRepository.findByRaceIdAndId(raceId, participant.getId())).thenReturn(Optional.of(participant));
        when(participantMapper.toDto(participant)).thenReturn(new ParticipantDto());

        RaceRegistrationField field = RaceRegistrationField.builder()
            .id(UUID.randomUUID())
            .race(race)
            .fieldKey("shirt_size")
            .label("Shirt size")
            .build();
        ParticipantRegistrationAnswer answer = ParticipantRegistrationAnswer.builder()
            .id(UUID.randomUUID())
            .participant(participant)
            .field(field)
            .value("M")
            .build();
        when(registrationAnswerRepository.findByParticipantId(participant.getId())).thenReturn(List.of(answer));

        ParticipantDto dto = service.getByRaceAndId(raceId, participant.getId());

        assertThat(dto.getAnswers()).hasSize(1);
        assertThat(dto.getAnswers().get(0).getValue()).isEqualTo("M");
        assertThat(dto.getAnswers().get(0).getLabel()).isEqualTo("Shirt size");
    }

    @Test
    void assignedStaffParticipantReadRedactsSensitiveFields() {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(email, null,
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_OFFICIAL"))));
        Participant participant = new Participant();
        participant.setId(UUID.randomUUID());
        participant.setRace(race);
        when(participantRepository.findByRaceIdAndId(raceId, participant.getId())).thenReturn(Optional.of(participant));

        ParticipantDto mapped = new ParticipantDto();
        mapped.setFirstName("Ada");
        mapped.setLastName("Runner");
        mapped.setBibNumber("42");
        mapped.setEmail("ada@example.com");
        mapped.setPhone("0712345678");
        mapped.setMedicalConditions("asthma");
        mapped.setPaymentReference("PAY-1");
        mapped.setCredentials(List.of(new ParticipantCredentialDto()));
        when(participantMapper.toDto(participant)).thenReturn(mapped);
        when(registrationAnswerRepository.findByParticipantId(participant.getId())).thenReturn(List.of());

        ParticipantDto dto = service.getByRaceAndId(raceId, participant.getId());

        assertThat(dto.getFirstName()).isEqualTo("Ada");
        assertThat(dto.getLastName()).isEqualTo("Runner");
        assertThat(dto.getBibNumber()).isEqualTo("42");
        assertThat(dto.getEmail()).isNull();
        assertThat(dto.getPhone()).isNull();
        assertThat(dto.getMedicalConditions()).isNull();
        assertThat(dto.getPaymentReference()).isNull();
        assertThat(dto.getCredentials()).isEmpty();
    }

    @Test
    void approvalRequiredMovesRegistrationToPendingReview() {
        race.setRequireApproval(true);
        when(participantRepository.countByRaceIdAndStatusIn(eq(raceId), any())).thenReturn(0L);

        RegistrationDto dto = service.registerForRace(raceId, request());

        assertThat(dto.getStatus()).isEqualTo("pending_review");
    }

    @Test
    void waiverRequiredMustBeAccepted() {
        race.setRequireWaiver(true);
        when(participantRepository.countByRaceIdAndStatusIn(eq(raceId), any())).thenReturn(0L);

        assertThatThrownBy(() -> service.registerForRace(raceId, request()))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("waiver");

        RaceRegistrationRequest accepted = request();
        accepted.setWaiverAccepted(true);
        RegistrationDto dto = service.registerForRace(raceId, accepted);
        assertThat(dto.getStatus()).isEqualTo("pending");
    }

    @Test
    void freeRaceRegistrationIsPaymentWaived() {
        when(participantRepository.countByRaceIdAndStatusIn(eq(raceId), any())).thenReturn(0L);

        RegistrationDto dto = service.registerForRace(raceId, request());

        assertThat(dto.getPaymentStatus()).isEqualTo("WAIVED");
    }

    @Test
    void paidRaceRegistrationStartsUnpaid() {
        race.setRegistrationFee(new BigDecimal("25.00"));
        when(participantRepository.countByRaceIdAndStatusIn(eq(raceId), any())).thenReturn(0L);

        RegistrationDto dto = service.registerForRace(raceId, request());

        assertThat(dto.getPaymentStatus()).isEqualTo("UNPAID");
    }

    @Test
    void missingRequiredCustomFieldIsRejected() {
        RaceRegistrationField field = RaceRegistrationField.builder()
            .id(UUID.randomUUID())
            .race(race)
            .fieldKey("shirt_size")
            .label("Shirt size")
            .fieldType(RaceRegistrationField.FieldType.TEXT)
            .required(true)
            .active(true)
            .build();
        when(registrationFieldRepository.findByRaceIdAndActiveTrueOrderByDisplayOrderAsc(raceId))
            .thenReturn(List.of(field));

        assertThatThrownBy(() -> service.registerForRace(raceId, request()))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("Missing required registration field");
    }

    @Test
    void approvalBlockedUntilPaymentVerified() {
        race.setRegistrationFee(new BigDecimal("50.00"));
        Participant participant = new Participant();
        participant.setId(UUID.randomUUID());
        participant.setRace(race);
        participant.setStatus(Participant.ParticipantStatus.PENDING_REVIEW);
        participant.setPaymentStatus(Participant.PaymentStatus.UNPAID);
        when(participantRepository.findByRaceIdAndIdForUpdate(raceId, participant.getId())).thenReturn(Optional.of(participant));

        RegistrationApprovalRequest approval = new RegistrationApprovalRequest();
        approval.setDecision("APPROVE");
        approval.setForce(false);

        assertThatThrownBy(() -> service.reviewRegistration(raceId, participant.getId(), approval, email))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("Payment must be verified");
    }

    @Test
    void verifyPaymentUpdatesStatus() {
        Participant participant = new Participant();
        participant.setId(UUID.randomUUID());
        participant.setRace(race);
        when(participantRepository.findByRaceIdAndIdForUpdate(raceId, participant.getId())).thenReturn(Optional.of(participant));
        when(participantMapper.toDto(any(Participant.class))).thenReturn(new ParticipantDto());

        PaymentVerifyRequest request = new PaymentVerifyRequest();
        request.setStatus("PAID");
        request.setReference("REF-1");
        request.setAmount(new BigDecimal("50.00"));

        service.verifyPayment(raceId, participant.getId(), request, email);

        assertThat(participant.getPaymentStatus()).isEqualTo(Participant.PaymentStatus.PAID);
        assertThat(participant.getPaymentReference()).isEqualTo("REF-1");
        assertThat(participant.getPaymentVerifiedAt()).isNotNull();
    }

    @Test
    void approvalAtCapacitySucceedsForAlreadyOccupyingParticipant() {
        race.setMaxParticipants(1);
        Participant participant = new Participant();
        participant.setId(UUID.randomUUID());
        participant.setRace(race);
        participant.setStatus(Participant.ParticipantStatus.PENDING_REVIEW);
        participant.setPaymentStatus(Participant.PaymentStatus.WAIVED);
        when(participantRepository.findByRaceIdAndIdForUpdate(raceId, participant.getId())).thenReturn(Optional.of(participant));
        when(participantMapper.toDto(any(Participant.class))).thenReturn(new ParticipantDto());

        RegistrationApprovalRequest approval = new RegistrationApprovalRequest();
        approval.setDecision("APPROVE");
        approval.setForce(false);

        service.reviewRegistration(raceId, participant.getId(), approval, email);

        assertThat(participant.getStatus()).isEqualTo(Participant.ParticipantStatus.CONFIRMED);
        assertThat(participant.getApprovedAt()).isNotNull();
    }

    @Test
    void waitingParticipantApprovalStillRespectsCapacity() {
        race.setMaxParticipants(1);
        Participant participant = new Participant();
        participant.setId(UUID.randomUUID());
        participant.setRace(race);
        participant.setStatus(Participant.ParticipantStatus.WAITLISTED);
        participant.setWaitlistPosition(1);
        participant.setPaymentStatus(Participant.PaymentStatus.WAIVED);
        when(participantRepository.findByRaceIdAndIdForUpdate(raceId, participant.getId())).thenReturn(Optional.of(participant));
        when(participantRepository.countByRaceIdAndStatusIn(eq(raceId), any())).thenReturn(1L);

        RegistrationApprovalRequest approval = new RegistrationApprovalRequest();
        approval.setDecision("APPROVE");
        approval.setForce(false);

        assertThatThrownBy(() -> service.reviewRegistration(raceId, participant.getId(), approval, email))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    void checkInRejectsUnapprovedParticipant() {
        Participant participant = new Participant();
        participant.setId(UUID.randomUUID());
        participant.setRace(race);
        participant.setStatus(Participant.ParticipantStatus.PENDING_REVIEW);
        when(participantRepository.findByRaceIdAndIdForUpdate(raceId, participant.getId())).thenReturn(Optional.of(participant));

        assertThatThrownBy(() -> service.checkIn(raceId, participant.getId()))
            .isInstanceOf(com.racetracker.exception.ConflictException.class)
            .extracting("code").isEqualTo("CHECK_IN_NOT_ELIGIBLE");
    }

    @Test
    void checkInLocksParticipantAndIsIdempotentAfterFirstSuccessfulCheckIn() {
        Participant participant = Participant.builder()
            .id(UUID.randomUUID())
            .race(race)
            .status(Participant.ParticipantStatus.CHECKED_IN)
            .checkedInAt(OffsetDateTime.now().minusMinutes(2))
            .build();
        ParticipantDto mapped = new ParticipantDto();
        when(participantRepository.findByRaceIdAndIdForUpdate(raceId, participant.getId()))
            .thenReturn(Optional.of(participant));
        when(participantMapper.toDto(participant)).thenReturn(mapped);

        ParticipantDto result = service.checkIn(raceId, participant.getId());

        assertThat(result).isSameAs(mapped);
        verify(participantRepository).findByRaceIdAndIdForUpdate(raceId, participant.getId());
        verify(participantRepository, never()).save(any(Participant.class));
        verifyNoInteractions(notificationService);
    }

    @Test
    void checkInEnforcesOperationalRaceAccessBeforeLoadingParticipant() {
        UUID participantId = UUID.randomUUID();
        doThrow(new AccessDeniedException("wrong race"))
            .when(raceAccessGuard).assertCanPerformOperationalAction(raceId);

        assertThatThrownBy(() -> service.checkIn(raceId, participantId))
            .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(participantRepository);
    }

    @Test
    void publicRegistrationFieldsHiddenForDraftRace() {
        race.setStatus(Race.RaceStatus.DRAFT);
        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));

        assertThatThrownBy(() -> service.listRegistrationFields(raceId, true))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void adminRegistrationFieldsVisibleForDraftRace() {
        race.setStatus(Race.RaceStatus.DRAFT);
        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
        when(registrationFieldRepository.findByRaceIdOrderByDisplayOrderAsc(raceId)).thenReturn(List.of());

        assertThat(service.listRegistrationFields(raceId, false)).isEmpty();
    }

    @Test
    void reviewRejectsTerminalState() {
        Participant participant = new Participant();
        participant.setId(UUID.randomUUID());
        participant.setRace(race);
        participant.setStatus(Participant.ParticipantStatus.FINISHED);
        when(participantRepository.findByRaceIdAndIdForUpdate(raceId, participant.getId())).thenReturn(Optional.of(participant));

        RegistrationApprovalRequest approval = new RegistrationApprovalRequest();
        approval.setDecision("APPROVE");

        assertThatThrownBy(() -> service.reviewRegistration(raceId, participant.getId(), approval, email))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("cannot be reviewed");
    }

    @Test
    void importUploadIsStoredWithTheJobNotOnLocalDisk(@org.junit.jupiter.api.io.TempDir java.nio.file.Path uploads) throws Exception {
        RaceTrackerProperties properties = new RaceTrackerProperties();
        properties.getStorage().setUploadDir(uploads.toString());
        ParticipantServiceImpl uploadService = new ParticipantServiceImpl(
            participantRepository, raceRepository, raceCategoryRepository, userRepository,
            importJobRepository, participantMapper, importJobMapper, properties,
            participantCredentialService, teamRepository, registrationFieldRepository,
            registrationAnswerRepository, registrationEmailService, notificationService, importProcessor,
            capabilityAuthorizationService, raceAccessGuard, participantIdentityService, importSourceStore,
            participantChangeControl);
        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
        when(importJobRepository.save(any(com.racetracker.model.ImportJob.class))).thenAnswer(inv -> {
            com.racetracker.model.ImportJob job = inv.getArgument(0);
            job.setId(UUID.randomUUID());
            return job;
        });
        byte[] csv = "firstName,lastName,email\n".getBytes();
        org.springframework.mock.web.MockMultipartFile upload = new org.springframework.mock.web.MockMultipartFile(
            "file", "../../../escape/evil.csv", "text/csv", csv);

        uploadService.importCsv(raceId, upload);

        org.mockito.ArgumentCaptor<com.racetracker.model.ImportJob> saved =
            org.mockito.ArgumentCaptor.forClass(com.racetracker.model.ImportJob.class);
        verify(importJobRepository).save(saved.capture());
        assertThat(saved.getValue().getSourcePath()).isNull();
        assertThat(saved.getValue().getFileName()).isEqualTo("evil.csv");
        verify(importSourceStore).save(saved.getValue().getId(), csv);
        verify(importProcessor).process(saved.getValue().getId());
        try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.walk(uploads)) {
            assertThat(files.filter(java.nio.file.Files::isRegularFile)).isEmpty();
        }
    }

    @Test
    void importRejectsUnsupportedFileTypes() {
        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
        org.springframework.mock.web.MockMultipartFile upload = new org.springframework.mock.web.MockMultipartFile(
            "file", "payload.sh", "text/plain", "echo".getBytes());

        assertThatThrownBy(() -> service.importCsv(raceId, upload))
            .isInstanceOf(com.racetracker.exception.ApiException.class)
            .hasMessageContaining(".csv");
        verifyNoInteractions(importProcessor);
    }

    @Test
    void updateRejectsABibAlreadyUsedInTheSameRaceBeforeTouchingTheEntity() {
        UUID participantId = UUID.randomUUID();
        when(participantRepository.existsByRaceIdAndBibNumberAndIdNot(raceId, "101", participantId)).thenReturn(true);
        com.racetracker.dto.participant.ParticipantUpdateRequest request = new com.racetracker.dto.participant.ParticipantUpdateRequest();
        request.setBibNumber("101");

        assertThatThrownBy(() -> service.update(raceId, participantId, request))
            .isInstanceOf(com.racetracker.exception.ConflictException.class)
            .hasMessageContaining("bib number");
        verify(participantRepository, never()).save(any());
        verify(participantRepository, never()).findByRaceIdAndId(raceId, participantId);
    }

    @Test
    void staffSearchIsBoundedStripsWildcardsAndReturnsNoContactDetails() {
        Participant found = Participant.builder().id(UUID.randomUUID()).race(race).firstName("Ada").lastName("Runner")
            .bibNumber("101").email("ada@example.com").phone("0710000000").status(Participant.ParticipantStatus.CONFIRMED).build();
        org.mockito.ArgumentCaptor<org.springframework.data.domain.Pageable> page =
            org.mockito.ArgumentCaptor.forClass(org.springframework.data.domain.Pageable.class);
        org.mockito.Mockito.when(participantRepository.searchForStaff(org.mockito.ArgumentMatchers.eq(raceId),
            org.mockito.ArgumentMatchers.eq("ada"), page.capture())).thenReturn(List.of(found));

        var results = service.searchForStaff(raceId, " %a_da% ");

        org.mockito.Mockito.verify(raceAccessGuard).assertCanReadStaff(raceId);
        assertThat(page.getValue().getPageSize()).isEqualTo(20);
        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.bibNumber()).isEqualTo("101");
            assertThat(result.status()).isEqualTo("CONFIRMED");
        });
        assertThat(com.racetracker.dto.participant.StaffParticipantSearchResultDto.class.getRecordComponents())
            .extracting(java.lang.reflect.RecordComponent::getName)
            .doesNotContain("email", "phone", "emergencyContactPhone", "medicalConditions");
    }

    @Test
    void registrationRequiresTheRaceToBeOpen() {
        for (Race.RaceStatus status : List.of(Race.RaceStatus.DRAFT, Race.RaceStatus.REGISTRATION_CLOSED,
            Race.RaceStatus.IN_PROGRESS, Race.RaceStatus.COMPLETED, Race.RaceStatus.CANCELLED)) {
            race.setStatus(status);
            assertThatThrownBy(() -> service.registerForRace(raceId, request()))
                .isInstanceOf(ApiException.class)
                .extracting("code").isEqualTo("REGISTRATION_NOT_OPEN");
        }
        verify(participantRepository, never()).save(any());
    }

    @Test
    void categoryFeeIsChargedEvenWhenTheRaceHasNoFee() {
        category.setRegistrationFee(new BigDecimal("30.00"));
        when(participantRepository.countByRaceIdAndStatusIn(eq(raceId), any())).thenReturn(0L);

        RegistrationDto dto = service.registerForRace(raceId, request());

        assertThat(dto.getPaymentStatus()).isEqualTo("UNPAID");
        assertThat(dto.getAmountDue()).isEqualByComparingTo("30.00");
    }

    @Test
    void freeCategoryInAPaidRaceIsWaived() {
        race.setRegistrationFee(new BigDecimal("25.00"));
        category.setRegistrationFee(BigDecimal.ZERO);
        when(participantRepository.countByRaceIdAndStatusIn(eq(raceId), any())).thenReturn(0L);

        RegistrationDto dto = service.registerForRace(raceId, request());

        assertThat(dto.getPaymentStatus()).isEqualTo("WAIVED");
    }

    @Test
    void approvalUsesTheFeeTheParticipantActuallyOwes() {
        category.setRegistrationFee(new BigDecimal("30.00"));
        Participant owes = reviewable(Participant.PaymentStatus.UNPAID);
        RegistrationApprovalRequest approval = new RegistrationApprovalRequest();
        approval.setDecision("APPROVE");

        assertThatThrownBy(() -> service.reviewRegistration(raceId, owes.getId(), approval, email))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("Payment must be verified");

        race.setRegistrationFee(new BigDecimal("25.00"));
        category.setRegistrationFee(BigDecimal.ZERO);
        Participant free = reviewable(Participant.PaymentStatus.UNPAID);
        when(participantMapper.toDto(any(Participant.class))).thenReturn(new ParticipantDto());

        service.reviewRegistration(raceId, free.getId(), approval, email);

        assertThat(free.getStatus()).isEqualTo(Participant.ParticipantStatus.CONFIRMED);
    }

    @Test
    void athleteWithdrawalMarksTheEntryWithdrawnAndRecordsTheChange() {
        Participant entry = ownEntry(Participant.ParticipantStatus.WAITLISTED);
        entry.setWaitlistPosition(3);

        service.cancelRegistration(entry.getId());

        assertThat(entry.getStatus()).isEqualTo(Participant.ParticipantStatus.WITHDRAWN);
        assertThat(entry.getStatusReason()).isEqualTo("Withdrawn by the athlete");
        assertThat(entry.getWaitlistPosition()).isNull();
        verify(participantChangeControl).verifyAndRecord(any(), eq(entry), eq("Withdrawn by the athlete"));
        verify(participantRepository).save(entry);
    }

    @Test
    void athleteCannotWithdrawToChangeAnOfficialResult() {
        for (Participant.ParticipantStatus status : List.of(Participant.ParticipantStatus.DISQUALIFIED,
            Participant.ParticipantStatus.DNF, Participant.ParticipantStatus.DNS, Participant.ParticipantStatus.FINISHED,
            Participant.ParticipantStatus.STARTED, Participant.ParticipantStatus.CHECKED_IN,
            Participant.ParticipantStatus.REJECTED, Participant.ParticipantStatus.WITHDRAWN)) {
            Participant entry = ownEntry(status);
            assertThatThrownBy(() -> service.cancelRegistration(entry.getId()))
                .isInstanceOf(com.racetracker.exception.ConflictException.class)
                .extracting("code").isEqualTo("REGISTRATION_NOT_CANCELLABLE");
            assertThat(entry.getStatus()).isEqualTo(status);
        }
        verify(participantRepository, never()).save(any());
        verifyNoInteractions(participantChangeControl);
    }

    @Test
    void athleteCannotWithdrawOnceTheRaceHasStarted() {
        for (Race.RaceStatus status : List.of(Race.RaceStatus.IN_PROGRESS, Race.RaceStatus.COMPLETED,
            Race.RaceStatus.CANCELLED)) {
            race.setStatus(status);
            Participant entry = ownEntry(Participant.ParticipantStatus.CONFIRMED);
            assertThatThrownBy(() -> service.cancelRegistration(entry.getId()))
                .isInstanceOf(com.racetracker.exception.ConflictException.class)
                .extracting("code").isEqualTo("REGISTRATION_NOT_CANCELLABLE");
        }
        verify(participantRepository, never()).save(any());
    }

    @Test
    void athleteCannotWithdrawSomeoneElsesEntry() {
        Participant entry = ownEntry(Participant.ParticipantStatus.CONFIRMED);
        User other = User.builder().id(UUID.randomUUID()).email("other@example.com").build();
        entry.setUser(other);

        assertThatThrownBy(() -> service.cancelRegistration(entry.getId()))
            .isInstanceOf(com.racetracker.exception.ResourceNotFoundException.class);
        assertThat(entry.getStatus()).isEqualTo(Participant.ParticipantStatus.CONFIRMED);
    }

    @Test
    void withdrawnEntriesShowAsCancelledToTheAthlete() {
        Participant entry = ownEntry(Participant.ParticipantStatus.WITHDRAWN);
        when(participantRepository.findByUserIdOrderByRegisteredAtDesc(user.getId())).thenReturn(List.of(entry));

        assertThat(service.getMyRegistrations()).singleElement()
            .extracting(RegistrationDto::getStatus).isEqualTo("cancelled");
    }

    private Participant reviewable(Participant.PaymentStatus paymentStatus) {
        Participant participant = new Participant();
        participant.setId(UUID.randomUUID());
        participant.setRace(race);
        participant.setCategory(category);
        participant.setStatus(Participant.ParticipantStatus.PENDING_REVIEW);
        participant.setPaymentStatus(paymentStatus);
        when(participantRepository.findByRaceIdAndIdForUpdate(raceId, participant.getId())).thenReturn(Optional.of(participant));
        return participant;
    }

    private Participant ownEntry(Participant.ParticipantStatus status) {
        Participant participant = new Participant();
        participant.setId(UUID.randomUUID());
        participant.setRace(race);
        participant.setCategory(category);
        participant.setUser(user);
        participant.setStatus(status);
        lenient().when(participantRepository.findByIdForUpdate(participant.getId())).thenReturn(Optional.of(participant));
        return participant;
    }

    @Test
    void staffSearchRejectsEmptyOrOverlongQueries() {
        assertThatThrownBy(() -> service.searchForStaff(raceId, " %% "))
            .isInstanceOf(ApiException.class).extracting("code").isEqualTo("PARTICIPANT_SEARCH_INVALID");
        assertThatThrownBy(() -> service.searchForStaff(raceId, "x".repeat(51)))
            .isInstanceOf(ApiException.class).extracting("code").isEqualTo("PARTICIPANT_SEARCH_INVALID");
    }
}
