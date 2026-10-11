package com.racetracker.service;

import com.racetracker.dto.emergency.EmergencyAlertDto;
import com.racetracker.dto.emergency.EmergencyAlertRequest;
import com.racetracker.model.EmergencyAlert;
import com.racetracker.model.Notification;
import com.racetracker.model.Participant;
import com.racetracker.model.Race;
import com.racetracker.model.User;
import com.racetracker.repository.EmergencyAlertRepository;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.RaceRepository;
import com.racetracker.repository.UserRepository;
import com.racetracker.service.impl.EmergencyAlertServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmergencyAlertServiceImplTest {

    @Mock EmergencyAlertRepository emergencyAlertRepository;
    @Mock RaceRepository raceRepository;
    @Mock ParticipantRepository participantRepository;
    @Mock UserRepository userRepository;
    @Mock NotificationService notificationService;

    private EmergencyAlertServiceImpl service;
    private final UUID raceId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new EmergencyAlertServiceImpl(emergencyAlertRepository, raceRepository,
            participantRepository, userRepository, notificationService);
    }

    @Test
    void raisePersistsAlertAndNotifiesRaceStaff() {
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
        assertThat(dto.getParticipantId()).isEqualTo(participant.getId());
        assertThat(dto.getMessage()).isEqualTo("Collapsed at km 12");
        verify(notificationService).notify(eq(organizer.getId()), eq(Notification.NotificationType.ALERT),
            any(), any(), any());
    }
}
