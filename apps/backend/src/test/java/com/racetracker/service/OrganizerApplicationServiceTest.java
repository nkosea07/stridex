package com.racetracker.service;

import com.racetracker.dto.auth.AuthResponse;
import com.racetracker.dto.auth.RegisterOrganizerRequest;
import com.racetracker.dto.auth.OrganizerApplicationDecisionRequest;
import com.racetracker.exception.ConflictException;
import com.racetracker.model.OrganizerApplication;
import com.racetracker.model.User;
import com.racetracker.repository.OrganizerApplicationRepository;
import com.racetracker.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class OrganizerApplicationServiceTest {

    @Mock
    private AuthService authService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrganizerApplicationRepository applicationRepository;

    @Mock
    private AuditEventService auditEventService;

    @Test
    void registerCreatesAStandardUserAndPendingApplication() {
        OrganizerApplicationService service = new OrganizerApplicationService(
            authService,
            userRepository,
            applicationRepository,
            auditEventService
        );
        RegisterOrganizerRequest request = new RegisterOrganizerRequest();
        request.setEmail(" organizer@example.com ");
        request.setPassword("password123");
        request.setFirstName("Race");
        request.setLastName("Organizer");
        request.setOrganizationName("City Run Club");
        User applicant = User.builder().email("organizer@example.com").role(User.UserRole.ATHLETE).build();
        AuthResponse response = AuthResponse.builder().requiresEmailVerification(true).user(null).build();

        when(authService.register(any())).thenReturn(response);
        when(userRepository.findByEmailIgnoreCase("organizer@example.com")).thenReturn(Optional.of(applicant));

        assertThat(service.register(request)).isSameAs(response);

        ArgumentCaptor<OrganizerApplication> captor = ArgumentCaptor.forClass(OrganizerApplication.class);
        verify(applicationRepository).save(captor.capture());
        assertThat(captor.getValue().getApplicant()).isSameAs(applicant);
        assertThat(captor.getValue().getOrganizationName()).isEqualTo("City Run Club");
        assertThat(captor.getValue().getStatus()).isEqualTo(OrganizerApplication.ApplicationStatus.PENDING);
        assertThat(applicant.getRole()).isEqualTo(User.UserRole.ATHLETE);
    }

    @Test
    void listApplicationsCapsPageSizeAtOneHundred() {
        OrganizerApplicationService service = new OrganizerApplicationService(authService, userRepository, applicationRepository, auditEventService);
        when(applicationRepository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, 100)))
            .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 100), 0));

        var page = service.listApplications(null, 0, 1000);

        assertThat(page.size()).isEqualTo(100);
        assertThat(page.items()).isEmpty();
    }

    @Test
    void approvalRequiresVerifiedEmailAndLeavesApplicantUnprivilegedOtherwise() {
        OrganizerApplicationService service = new OrganizerApplicationService(authService, userRepository, applicationRepository, auditEventService);
        User applicant = User.builder().id(UUID.randomUUID()).email("organizer@example.com")
            .role(User.UserRole.ATHLETE).emailVerified(false).build();
        User reviewer = User.builder().id(UUID.randomUUID()).email("admin@example.com").build();
        OrganizerApplication application = OrganizerApplication.builder()
            .id(UUID.randomUUID()).applicant(applicant).organizationName("City Run")
            .status(OrganizerApplication.ApplicationStatus.PENDING).build();
        OrganizerApplicationDecisionRequest request = new OrganizerApplicationDecisionRequest();
        request.setDecision(OrganizerApplicationDecisionRequest.Decision.APPROVE);
        when(userRepository.findByEmailIgnoreCase("admin@example.com")).thenReturn(Optional.of(reviewer));
        when(applicationRepository.findForDecisionById(application.getId())).thenReturn(Optional.of(application));

        assertThrows(ConflictException.class, () -> service.decide(application.getId(), request, "admin@example.com"));
        assertThat(applicant.getRole()).isEqualTo(User.UserRole.ATHLETE);
        assertThat(application.getStatus()).isEqualTo(OrganizerApplication.ApplicationStatus.PENDING);
    }

    @Test
    void approvalPromotesVerifiedApplicantButStillLeavesMfaEnforcementToTheAuthBoundary() {
        OrganizerApplicationService service = new OrganizerApplicationService(authService, userRepository, applicationRepository, auditEventService);
        User applicant = User.builder().id(UUID.randomUUID()).email("organizer@example.com")
            .firstName("Race").lastName("Lead").role(User.UserRole.ATHLETE).emailVerified(true).build();
        User reviewer = User.builder().id(UUID.randomUUID()).email("admin@example.com").build();
        OrganizerApplication application = OrganizerApplication.builder()
            .id(UUID.randomUUID()).applicant(applicant).organizationName("City Run")
            .status(OrganizerApplication.ApplicationStatus.PENDING).build();
        OrganizerApplicationDecisionRequest request = new OrganizerApplicationDecisionRequest();
        request.setDecision(OrganizerApplicationDecisionRequest.Decision.APPROVE);
        when(userRepository.findByEmailIgnoreCase("admin@example.com")).thenReturn(Optional.of(reviewer));
        when(applicationRepository.findForDecisionById(application.getId())).thenReturn(Optional.of(application));
        when(applicationRepository.save(application)).thenReturn(application);

        var result = service.decide(application.getId(), request, "admin@example.com");

        assertThat(result.status()).isEqualTo(OrganizerApplication.ApplicationStatus.APPROVED);
        assertThat(applicant.getRole()).isEqualTo(User.UserRole.RACE_ADMIN);
        assertThat(application.getReviewedBy()).isSameAs(reviewer);
    }
}
