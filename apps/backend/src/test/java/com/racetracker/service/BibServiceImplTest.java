package com.racetracker.service;

import com.racetracker.exception.ApiException;
import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.RaceRepository;
import com.racetracker.service.impl.BibServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BibServiceImplTest {

    @Mock
    private ParticipantRepository participantRepository;

    @Mock
    private RaceRepository raceRepository;

    @InjectMocks
    private BibServiceImpl bibService;

    @Test
    void generateBatchBibsPdf_rejectsParticipantOutsideRequestedRace() {
        UUID raceId = UUID.randomUUID();
        UUID firstParticipantId = UUID.randomUUID();
        UUID secondParticipantId = UUID.randomUUID();
        when(participantRepository.findByRaceIdAndIdIn(eq(raceId), anyCollection()))
            .thenReturn(List.of());

        assertThatThrownBy(() -> bibService.generateBatchBibsPdf(raceId, List.of(firstParticipantId, secondParticipantId)))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessage("One or more participants were not found in this race");
    }

    @Test
    void generateBatchBibsPdf_rejectsDuplicateParticipantIds() {
        UUID raceId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();

        assertThatThrownBy(() -> bibService.generateBatchBibsPdf(raceId, List.of(participantId, participantId)))
            .isInstanceOf(ApiException.class)
            .hasMessage("Participant IDs must be unique for batch printing");

        verifyNoInteractions(participantRepository);
    }
}
