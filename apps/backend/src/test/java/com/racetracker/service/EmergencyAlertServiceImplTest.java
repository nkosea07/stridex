package com.racetracker.service;

import com.racetracker.dto.emergency.EmergencyAlertDto;
import com.racetracker.dto.emergency.EmergencyAlertRequest;
import com.racetracker.exception.ApiException;
import com.racetracker.model.EmergencyAlert;
import com.racetracker.model.EmergencyNotificationJob;
import com.racetracker.model.NotificationDelivery;
import com.racetracker.model.Participant;
import com.racetracker.model.Race;
import com.racetracker.model.User;
import com.racetracker.repository.EmergencyAlertRepository;
import com.racetracker.repository.EmergencyNotificationJobRepository;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.RaceRepository;
import com.racetracker.repository.RaceStaffAssignmentRepository;
import com.racetracker.repository.UserRepository;
import com.racetracker.service.impl.EmergencyAlertServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmergencyAlertServiceImplTest {

    @Mock EmergencyAlertRepository emergencyAlertRepository;
    @Mock RaceRepository raceRepository;
    @Mock ParticipantRepository participantRepository;
    @Mock UserRepository userRepository;
    @Mock RaceStaffAssignmentRepository staffRepository;
    @Mock EmergencyNotificationJobRepository jobRepository;

    private EmergencyAlertServiceImpl service;
    private final UUID raceId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new EmergencyAlertServiceImpl(emergencyAlertRepository, raceRepository,
            participantRepository, userRepository, staffRepository, jobRepository,
            List.of(new TestChannel(NotificationDelivery.Channel.PUSH, true)));
    }

    @Test
    void raisePersistsAlertAndStagesStaffNotificationJobs() {
        User organizer = User.builder().id(UUID.randomUUID()).email("organizer@example.com").build();
        Race race = new Race();
        race.setId(raceId);
        race.setCreatedBy(organizer);

        User athlete = User.builder().id(UUID.randomUUID()).email("athlete@example.com").firstName("Ada").build();
        Participant participant = Participant.builder().id(UUID.randomUUID()).race(race)
            .firstName("Ada").lastName("Runner").bibNumber("101").build();

        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
        when(userRepository.findByEmailIgnoreCase("athlete@example.com")).thenReturn(Optional.of(athlete));
        when(participantRepository.findByRaceIdAndUserId(raceId, athlete.getId())).thenReturn(Optional.of(participant));
        when(staffRepository.findActiveStaff(raceId)).thenReturn(List.of(organizer));
        when(emergencyAlertRepository.save(any(EmergencyAlert.class))).thenAnswer(inv -> {
            EmergencyAlert alert = inv.getArgument(0);
            alert.setId(UUID.randomUUID());
            return alert;
        });

        EmergencyAlertRequest request = new EmergencyAlertRequest();
        request.setLatitude(BigDecimal.valueOf(-33.9));
        request.setLongitude(BigDecimal.valueOf(18.4));
        request.setMessage("Collapsed at km 12");

        EmergencyAlertDto dto = service.raise(raceId, request, "athlete@example.com");

        assertThat(dto.getStatus()).isEqualTo("ACTIVE");
        assertThat(dto.getDeliveryStatus()).isEqualTo("QUEUED");
        assertThat(dto.getParticipantId()).isEqualTo(participant.getId());
        assertThat(dto.getMessage()).isEqualTo("Collapsed at km 12");
        ArgumentCaptor<EmergencyNotificationJob> jobs = ArgumentCaptor.forClass(EmergencyNotificationJob.class);
        verify(jobRepository, org.mockito.Mockito.times(2)).save(jobs.capture());
        assertThat(jobs.getAllValues()).allSatisfy(job -> {
            assertThat(job.getRecipient().getId()).isEqualTo(organizer.getId());
            assertThat(job.getStatus()).isEqualTo(EmergencyNotificationJob.Status.PENDING);
        });
        assertThat(jobs.getAllValues()).extracting(EmergencyNotificationJob::getChannel)
            .containsExactlyInAnyOrder(NotificationDelivery.Channel.IN_APP, NotificationDelivery.Channel.PUSH);
    }

    @Test
    void resolvingAlertCancelsPendingNotificationJobs() {
        Race race = new Race();
        race.setId(raceId);
        User organizer = User.builder().id(UUID.randomUUID()).email("organizer@example.com").build();
        EmergencyAlert alert = EmergencyAlert.builder().id(UUID.randomUUID()).race(race)
            .status(EmergencyAlert.EmergencyStatus.ACTIVE)
            .deliveryStatus(EmergencyAlert.DeliveryStatus.ESCALATION_REQUIRED).build();
        when(emergencyAlertRepository.findById(alert.getId())).thenReturn(Optional.of(alert));
        when(userRepository.findByEmailIgnoreCase(organizer.getEmail())).thenReturn(Optional.of(organizer));
        when(emergencyAlertRepository.save(any(EmergencyAlert.class))).thenAnswer(inv -> inv.getArgument(0));

        EmergencyAlertDto dto = service.updateStatus(raceId, alert.getId(), "RESOLVED", organizer.getEmail());

        assertThat(dto.getStatus()).isEqualTo("RESOLVED");
        assertThat(alert.getAcknowledgedBy()).isEqualTo(organizer.getId());
        assertThat(alert.getAcknowledgedAt()).isNotNull();
        verify(jobRepository).cancelPendingForAlert(alert.getId());
    }

    @Test
    void sosWithoutRecipientsIsMarkedForEscalation() {
        Race race = new Race();
        race.setId(raceId);
        User athlete = User.builder().id(UUID.randomUUID()).email("athlete@example.com").build();
        Participant participant = Participant.builder().id(UUID.randomUUID()).race(race)
            .firstName("Ada").lastName("Runner").build();
        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
        when(userRepository.findByEmailIgnoreCase(athlete.getEmail())).thenReturn(Optional.of(athlete));
        when(participantRepository.findByRaceIdAndUserId(raceId, athlete.getId())).thenReturn(Optional.of(participant));
        when(staffRepository.findActiveStaff(raceId)).thenReturn(List.of());
        when(emergencyAlertRepository.save(any(EmergencyAlert.class))).thenAnswer(inv -> {
            EmergencyAlert alert = inv.getArgument(0);
            alert.setId(UUID.randomUUID());
            return alert;
        });

        EmergencyAlertDto dto = service.raiseForAthlete(raceId, new EmergencyAlertRequest(), athlete.getEmail());

        assertThat(dto.getDeliveryStatus()).isEqualTo("ESCALATION_REQUIRED");
        verify(jobRepository, never()).save(any());
    }

    @Test
    void sosAcceptsNoLocationButRejectsPartialOrOutOfRangeCoordinates() {
        User athlete = User.builder().id(UUID.randomUUID()).email("athlete@example.com").build();

        EmergencyAlertRequest partial = new EmergencyAlertRequest();
        partial.setLatitude(BigDecimal.valueOf(-33.9));
        assertThatThrownBy(() -> service.raiseForAthlete(raceId, partial, athlete.getEmail()))
            .isInstanceOf(ApiException.class)
            .extracting("code").isEqualTo("EMERGENCY_INVALID_COORDINATES");
        verify(emergencyAlertRepository, never()).save(any());

        EmergencyAlertRequest outOfRange = new EmergencyAlertRequest();
        outOfRange.setLatitude(BigDecimal.valueOf(91));
        outOfRange.setLongitude(BigDecimal.valueOf(18.4));
        assertThatThrownBy(() -> service.raiseForAthlete(raceId, outOfRange, athlete.getEmail()))
            .isInstanceOf(ApiException.class)
            .extracting("code").isEqualTo("EMERGENCY_INVALID_COORDINATES");
    }

    @Test
    void rejectsInvalidAlertStatus() {
        assertThatThrownBy(() -> service.updateStatus(raceId, UUID.randomUUID(), "ACTIVE", "admin@example.com"))
            .isInstanceOf(ApiException.class)
            .extracting("code").isEqualTo("EMERGENCY_STATUS_INVALID");
    }

    @Test
    void athleteCannotRaiseAlertForAnotherRace() {
        Race race = new Race();
        race.setId(raceId);
        User athlete = User.builder().id(UUID.randomUUID()).email("athlete@example.com").build();
        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
        when(userRepository.findByEmailIgnoreCase(athlete.getEmail())).thenReturn(Optional.of(athlete));

        assertThatThrownBy(() -> service.raiseForAthlete(raceId, new EmergencyAlertRequest(), athlete.getEmail()))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        org.mockito.Mockito.verifyNoInteractions(emergencyAlertRepository);
    }

    private record TestChannel(NotificationDelivery.Channel channel, boolean enabled) implements NotificationChannel {
        @Override
        public NotificationDelivery.Channel channel() {
            return channel;
        }

        @Override
        public boolean isEnabled() {
            return enabled;
        }

        @Override
        public void send(com.racetracker.notification.NotificationMessage message) {
        }
    }
}
