package com.racetracker.service;

import com.racetracker.dto.participant.PenaltyDto;
import com.racetracker.dto.participant.PenaltyRequest;
import com.racetracker.exception.ApiException;
import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.model.Participant;
import com.racetracker.model.ParticipantPenalty;
import com.racetracker.model.Race;
import com.racetracker.repository.ParticipantPenaltyRepository;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.UserRepository;
import com.racetracker.service.impl.PenaltyServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PenaltyServiceImplTest {

    @Mock ParticipantPenaltyRepository penaltyRepository;
    @Mock ParticipantRepository participantRepository;
    @Mock UserRepository userRepository;

    private PenaltyServiceImpl service;
    private final UUID raceId = UUID.randomUUID();
    private final UUID participantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new PenaltyServiceImpl(penaltyRepository, participantRepository, userRepository);
        lenient().when(penaltyRepository.save(any(ParticipantPenalty.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private Participant participant() {
        Participant participant = new Participant();
        participant.setId(participantId);
        participant.setRace(new Race());
        return participant;
    }

    @Test
    void addStoresPenaltyForParticipant() {
        when(participantRepository.findByRaceIdAndId(raceId, participantId)).thenReturn(Optional.of(participant()));

        PenaltyRequest request = new PenaltyRequest();
        request.setSeconds(120);
        request.setReason("Missed checkpoint");

        PenaltyDto dto = service.add(raceId, participantId, request, null);

        assertThat(dto.getSeconds()).isEqualTo(120);
        assertThat(dto.getReason()).isEqualTo("Missed checkpoint");
        assertThat(dto.getParticipantId()).isEqualTo(participantId);
    }

    @Test
    void addRejectsZeroSeconds() {
        when(participantRepository.findByRaceIdAndId(raceId, participantId)).thenReturn(Optional.of(participant()));

        PenaltyRequest request = new PenaltyRequest();
        request.setSeconds(0);

        assertThatThrownBy(() -> service.add(raceId, participantId, request, null))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("non-zero");
    }

    @Test
    void removeRejectsUnknownPenalty() {
        when(penaltyRepository.findByRaceIdAndId(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.remove(raceId, UUID.randomUUID()))
            .isInstanceOf(ResourceNotFoundException.class);
    }
}
