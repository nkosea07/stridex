package com.racetracker.service;

import com.racetracker.exception.ApiException;
import com.racetracker.exception.ConflictException;
import com.racetracker.mapper.ParticipantMapper;
import com.racetracker.model.Race;
import com.racetracker.repository.CheckpointRecordRepository;
import com.racetracker.repository.ParticipantPenaltyRepository;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.RaceCategoryRepository;
import com.racetracker.repository.RaceRepository;
import com.racetracker.repository.RaceResultRepository;
import com.racetracker.repository.RaceResultRevisionRepository;
import com.racetracker.repository.UserRepository;
import com.racetracker.service.impl.ResultServiceImpl;
import com.racetracker.security.CapabilityAuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResultServiceImplPaginationTest {

    @Mock private RaceRepository raceRepository;
    @Mock private ParticipantRepository participantRepository;
    @Mock private CheckpointRecordRepository checkpointRecordRepository;
    @Mock private UserRepository userRepository;
    @Mock private ParticipantMapper participantMapper;
    @Mock private RaceResultRevisionRepository resultRevisionRepository;
    @Mock private ParticipantPenaltyRepository penaltyRepository;
    @Mock private RaceResultRepository raceResultRepository;
    @Mock private RaceCategoryRepository raceCategoryRepository;
    @Mock private CapabilityAuthorizationService capabilityAuthorizationService;

    private ResultServiceImpl resultService;
    private final UUID raceId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        resultService = new ResultServiceImpl(raceRepository, participantRepository, checkpointRecordRepository,
            userRepository, participantMapper, resultRevisionRepository, penaltyRepository,
            raceResultRepository, raceCategoryRepository, capabilityAuthorizationService);
        when(raceRepository.findById(raceId)).thenReturn(Optional.of(org.mockito.Mockito.mock(Race.class)));
    }

    @Test
    void rejectsLaterPageWhenResultRevisionHasChanged() {
        when(resultRevisionRepository.currentRevision(raceId)).thenReturn(12L);

        assertThatThrownBy(() -> resultService.listByRacePage(raceId, 1, 50, 11L))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("restart pagination");

        verifyNoInteractions(participantRepository);
    }

    @Test
    void requiresRevisionForEveryLaterPage() {
        when(resultRevisionRepository.currentRevision(raceId)).thenReturn(12L);

        assertThatThrownBy(() -> resultService.listByRacePage(raceId, 1, 50, null))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("revision is required");

        verifyNoInteractions(participantRepository);
    }

    @Test
    void allowsFirstPageWithoutAnExpectedRevision() {
        when(resultRevisionRepository.currentRevision(raceId)).thenReturn(12L);
        when(participantRepository.findResultRows(any(), any(), anyBoolean(), any(), any()))
            .thenReturn(java.util.List.of());
        when(participantRepository.countResultRows(any(), any(), anyBoolean())).thenReturn(0L);

        var page = resultService.listByRacePage(raceId, 0, 50, null);

        org.assertj.core.api.Assertions.assertThat(page.revision()).isEqualTo(12L);
        verify(participantRepository).findResultRows(eq(raceId), isNull(), eq(false), isNull(),
            org.mockito.ArgumentMatchers.any(org.springframework.data.domain.Pageable.class));
    }
}
