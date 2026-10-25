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

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaConsentServiceTest {

    @Mock ParticipantRepository participantRepository;
    @Mock UserRepository userRepository;
    @Mock AuditEventService auditEventService;

    @Test
    void ownerCanGrantAndRevokeMediaPublicationConsent() {
        UUID userId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        Participant participant = new Participant();
        participant.setId(participantId);
        participant.setUser(user);
        when(userRepository.findByEmailIgnoreCase("athlete@example.test")).thenReturn(Optional.of(user));
        when(participantRepository.findByIdForUpdate(participantId)).thenReturn(Optional.of(participant));
        MediaConsentService service = new MediaConsentService(participantRepository, userRepository, auditEventService);

        var granted = service.update(participantId, "athlete@example.test", true);
        assertThat(granted.consented()).isTrue();
        assertThat(granted.version()).isEqualTo("MEDIA_PUBLICATION_V1");
        assertThat(granted.acceptedAt()).isNotNull();

        var revoked = service.update(participantId, "athlete@example.test", false);
        assertThat(revoked.consented()).isFalse();
        assertThat(revoked.version()).isNull();
        assertThat(revoked.acceptedAt()).isNull();
        verify(auditEventService).recordInCurrentTransaction(
            "MEDIA_PUBLICATION_CONSENT_CHANGED", userId, "PARTICIPANT", participantId.toString(), "SUCCESS",
            java.util.Map.of("consented", true));
        verify(auditEventService).recordInCurrentTransaction(
            "MEDIA_PUBLICATION_CONSENT_CHANGED", userId, "PARTICIPANT", participantId.toString(), "SUCCESS",
            java.util.Map.of("consented", false));
    }

    @Test
    void anotherUserCannotChangeParticipantMediaConsent() {
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
        MediaConsentService service = new MediaConsentService(participantRepository, userRepository, auditEventService);

        assertThatThrownBy(() -> service.update(participantId, "other@example.test", true))
            .isInstanceOf(ResourceNotFoundException.class);
    }
}
