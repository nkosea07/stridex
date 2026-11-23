package com.racetracker.service;

import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.model.Participant;
import com.racetracker.model.User;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocationSharingServiceTest {

    @Mock ParticipantRepository participantRepository;
    @Mock UserRepository userRepository;
    @Mock AuditEventService auditEventService;

    @Test
    void newRegistrationsSharePubliclyUntilTheAthleteOptsOut() {
        assertThat(new Participant().isPublicLocationSharing()).isTrue();
        assertThat(Participant.builder().build().isPublicLocationSharing()).isTrue();
    }

    @Test
    void ownerCanStopAndResumePublicLocationSharing() {
        UUID userId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        Participant participant = new Participant();
        participant.setId(participantId);
        participant.setUser(user);
        when(userRepository.findByEmailIgnoreCase("athlete@example.test")).thenReturn(Optional.of(user));
        when(participantRepository.findByIdForUpdate(participantId)).thenReturn(Optional.of(participant));
        LocationSharingService service = new LocationSharingService(participantRepository, userRepository, auditEventService);

        var stopped = service.update(participantId, "athlete@example.test", false);
        assertThat(stopped.enabled()).isFalse();
        assertThat(participant.isPublicLocationSharing()).isFalse();

        var resumed = service.update(participantId, "athlete@example.test", true);
        assertThat(resumed.enabled()).isTrue();
        assertThat(participant.isPublicLocationSharing()).isTrue();
        verify(auditEventService).recordInCurrentTransaction(
            "PUBLIC_LOCATION_SHARING_CHANGED", userId, "PARTICIPANT", participantId.toString(), "SUCCESS",
            Map.of("enabled", false));
        verify(auditEventService).recordInCurrentTransaction(
            "PUBLIC_LOCATION_SHARING_CHANGED", userId, "PARTICIPANT", participantId.toString(), "SUCCESS",
            Map.of("enabled", true));
    }

    @Test
    void anotherUserCannotChangeLocationSharing() {
        User owner = new User();
        owner.setId(UUID.randomUUID());
        User actor = new User();
        actor.setId(UUID.randomUUID());
        UUID participantId = UUID.randomUUID();
        Participant participant = new Participant();
        participant.setId(participantId);
        participant.setUser(owner);
        when(userRepository.findByEmailIgnoreCase("other@example.test")).thenReturn(Optional.of(actor));
        when(participantRepository.findByIdForUpdate(participantId)).thenReturn(Optional.of(participant));
        LocationSharingService service = new LocationSharingService(participantRepository, userRepository, auditEventService);

        assertThatThrownBy(() -> service.update(participantId, "other@example.test", false))
            .isInstanceOf(ResourceNotFoundException.class);
        assertThat(participant.isPublicLocationSharing()).isTrue();
        verify(auditEventService, never()).recordInCurrentTransaction(anyString(), any(), anyString(), anyString(),
            anyString(), any());
    }
}
