package com.racetracker.service;

import com.racetracker.dto.auth.AuthResponse;
import com.racetracker.dto.auth.RegisterOrganizerRequest;
import com.racetracker.dto.auth.RegisterRequest;
import com.racetracker.dto.auth.OrganizerApplicationDecisionRequest;
import com.racetracker.dto.auth.OrganizerApplicationPageDto;
import com.racetracker.dto.auth.OrganizerApplicationReviewDto;
import com.racetracker.exception.ApiException;
import com.racetracker.exception.ConflictException;
import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.exception.UnauthorizedException;
import com.racetracker.model.OrganizerApplication;
import com.racetracker.model.User;
import com.racetracker.repository.OrganizerApplicationRepository;
import com.racetracker.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.UUID;

@Service
public class OrganizerApplicationService {

    private final AuthService authService;
    private final UserRepository userRepository;
    private final OrganizerApplicationRepository applicationRepository;
    private final AuditEventService auditEventService;

    public OrganizerApplicationService(
        AuthService authService,
        UserRepository userRepository,
        OrganizerApplicationRepository applicationRepository,
        AuditEventService auditEventService
    ) {
        this.authService = authService;
        this.userRepository = userRepository;
        this.applicationRepository = applicationRepository;
        this.auditEventService = auditEventService;
    }

    @Transactional
    public AuthResponse register(RegisterOrganizerRequest request) {
        RegisterRequest userRequest = new RegisterRequest();
        userRequest.setEmail(request.getEmail());
        userRequest.setPassword(request.getPassword());
        userRequest.setFirstName(request.getFirstName());
        userRequest.setLastName(request.getLastName());
        userRequest.setPhone(request.getPhone());
        userRequest.setPrivacyPolicyAccepted(request.getPrivacyPolicyAccepted());
        userRequest.setPrivacyPolicyVersion(request.getPrivacyPolicyVersion());
        userRequest.setCreateNormalCapabilities(false);

        AuthResponse response = authService.register(userRequest);
        User applicant = userRepository.findByEmailIgnoreCase(request.getEmail().trim())
            .orElseThrow(() -> new IllegalStateException("Organizer applicant was not created"));

        applicationRepository.save(OrganizerApplication.builder()
            .applicant(applicant)
            .organizationName(request.getOrganizationName().trim())
            .status(OrganizerApplication.ApplicationStatus.PENDING)
            .build());

        return response;
    }

    @Transactional(readOnly = true)
    public OrganizerApplicationPageDto listApplications(String statusFilter, int page, int size) {
        if (page < 0 || size < 1) {
            throw new ApiException("Page must be non-negative and size must be positive");
        }
        int boundedSize = Math.min(size, 100);
        PageRequest pageable = PageRequest.of(page, boundedSize);
        Page<OrganizerApplication> applications;
        if (statusFilter == null || statusFilter.isBlank()) {
            applications = applicationRepository.findAllByOrderByCreatedAtDesc(pageable);
        } else {
            OrganizerApplication.ApplicationStatus status;
            try {
                status = OrganizerApplication.ApplicationStatus.valueOf(statusFilter.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new ApiException("Organizer application status must be PENDING, APPROVED, or REJECTED");
            }
            applications = applicationRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        }

        return new OrganizerApplicationPageDto(
            applications.getContent().stream().map(this::toReviewDto).toList(),
            applications.getNumber(),
            applications.getSize(),
            applications.getTotalElements(),
            applications.getTotalPages()
        );
    }

    @Transactional
    public OrganizerApplicationReviewDto decide(
        UUID applicationId,
        OrganizerApplicationDecisionRequest request,
        String reviewerEmail
    ) {
        User reviewer = userRepository.findByEmailIgnoreCase(reviewerEmail)
            .orElseThrow(() -> new UnauthorizedException("Unauthorized"));
        OrganizerApplication application = applicationRepository.findForDecisionById(applicationId)
            .orElseThrow(() -> new ResourceNotFoundException("Organizer application not found"));
        if (application.getStatus() != OrganizerApplication.ApplicationStatus.PENDING) {
            throw new ConflictException("Organizer application has already been reviewed");
        }

        User applicant = application.getApplicant();
        if (request.getDecision() == OrganizerApplicationDecisionRequest.Decision.APPROVE) {
            if (!Boolean.TRUE.equals(applicant.getEmailVerified())) {
                throw new ConflictException("Applicant must verify their email before approval");
            }
            applicant.setRole(User.UserRole.RACE_ADMIN);
            userRepository.save(applicant);
            application.setStatus(OrganizerApplication.ApplicationStatus.APPROVED);
        } else {
            application.setStatus(OrganizerApplication.ApplicationStatus.REJECTED);
        }

        String notes = request.getReviewNotes();
        application.setReviewNotes(notes == null || notes.isBlank() ? null : notes.trim());
        application.setReviewedBy(reviewer);
        application.setReviewedAt(OffsetDateTime.now());
        OrganizerApplication saved = applicationRepository.save(application);
        auditEventService.recordInCurrentTransaction("ORGANIZER_APPLICATION_DECISION", reviewer.getId(),
            "ORGANIZER_APPLICATION", application.getId().toString(), "SUCCESS",
            java.util.Map.of("decision", request.getDecision().name(), "resultingStatus", application.getStatus().name()));
        return toReviewDto(saved);
    }

    private OrganizerApplicationReviewDto toReviewDto(OrganizerApplication application) {
        User applicant = application.getApplicant();
        return new OrganizerApplicationReviewDto(
            application.getId(),
            applicant.getEmail(),
            applicant.getFirstName(),
            applicant.getLastName(),
            Boolean.TRUE.equals(applicant.getEmailVerified()),
            application.getOrganizationName(),
            application.getStatus(),
            application.getReviewNotes(),
            application.getCreatedAt(),
            application.getReviewedAt(),
            application.getReviewedBy() == null ? null : application.getReviewedBy().getEmail()
        );
    }
}
