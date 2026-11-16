package com.racetracker.service;

import com.racetracker.dto.result.ResultQueryRequest;
import com.racetracker.dto.result.ResultQueryResolution;
import com.racetracker.exception.ConflictException;
import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.model.Participant;
import com.racetracker.model.Race;
import com.racetracker.model.ResultQuery;
import com.racetracker.model.User;
import com.racetracker.repository.CheckpointRepository;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.RaceRepository;
import com.racetracker.repository.ResultQueryRepository;
import com.racetracker.repository.UserRepository;
import com.racetracker.security.RaceAccessGuard;
import com.racetracker.service.impl.ResultQueryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResultQueryServiceTest {

    private final ResultQueryRepository queries = mock(ResultQueryRepository.class);
    private final ParticipantRepository participants = mock(ParticipantRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final NotificationService notifications = mock(NotificationService.class);
    private ResultQueryService service;
    private final User athlete = User.builder().id(UUID.randomUUID()).email("runner@example.com").build();
    private Race race;
    private Participant entry;

    @BeforeEach
    void setUp() {
        service = new ResultQueryService(queries, participants, mock(CheckpointRepository.class), users,
            mock(RaceAccessGuard.class), new ResultLock(mock(RaceRepository.class)), mock(AuditEventService.class), notifications);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("runner@example.com", null, List.of()));
        when(users.findByEmailIgnoreCase("runner@example.com")).thenReturn(Optional.of(athlete));
        race = Race.builder().id(UUID.randomUUID()).status(Race.RaceStatus.COMPLETED).build();
        entry = Participant.builder().id(UUID.randomUUID()).race(race).bibNumber("101").build();
        when(queries.save(any(ResultQuery.class))).thenAnswer(invocation -> {
            ResultQuery query = invocation.getArgument(0);
            if (query.getId() == null) {
                query.setId(UUID.randomUUID());
            }
            return query;
        });
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private ResultQueryRequest request() {
        ResultQueryRequest request = new ResultQueryRequest();
        request.setMessage("My watch shows I crossed the 10 km mat at 49:10");
        return request;
    }

    @Test
    void athletesCanOnlyQueryTheirOwnEntry() {
        when(participants.findByRaceIdAndUserId(race.getId(), athlete.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submit(race.getId(), request())).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void submittingAndAnsweringAQueryNotifiesTheAthlete() {
        when(participants.findByRaceIdAndUserId(race.getId(), athlete.getId())).thenReturn(Optional.of(entry));
        var submitted = service.submit(race.getId(), request());
        assertThat(submitted.status()).isEqualTo("OPEN");

        ResultQuery stored = new ResultQuery();
        stored.setId(UUID.randomUUID());
        stored.setRaceId(race.getId());
        stored.setParticipant(entry);
        stored.setSubmittedBy(athlete.getId());
        stored.setMessage("x");
        stored.setStatus(ResultQuery.Status.OPEN);
        when(queries.findByIdAndRaceIdForUpdate(stored.getId(), race.getId())).thenReturn(Optional.of(stored));
        ResultQueryResolution resolution = new ResultQueryResolution();
        resolution.setOutcome(ResultQueryResolution.Outcome.RESOLVED);
        resolution.setNote("Corrected the 10 km split after reviewing the backup mat");

        assertThat(service.resolve(race.getId(), stored.getId(), resolution).status()).isEqualTo("RESOLVED");
        verify(notifications).notifyLocalized(eq(athlete.getId()), any(), eq("RESULT_QUERY_RESOLVED"), any(), any());
        assertThatThrownBy(() -> service.resolve(race.getId(), stored.getId(), resolution))
            .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("RESULT_QUERY_CLOSED");
    }

    @Test
    void openQueriesAreLimitedAndCertifiedRacesTakeNoMore() {
        when(participants.findByRaceIdAndUserId(race.getId(), athlete.getId())).thenReturn(Optional.of(entry));
        when(queries.countByParticipantIdAndStatus(entry.getId(), ResultQuery.Status.OPEN)).thenReturn(3L);
        assertThatThrownBy(() -> service.submit(race.getId(), request()))
            .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("RESULT_QUERY_LIMIT");

        race.setResultsCertifiedAt(OffsetDateTime.now());
        assertThatThrownBy(() -> service.submit(race.getId(), request()))
            .isInstanceOf(ConflictException.class).extracting("code").isEqualTo(ResultLock.RESULTS_CERTIFIED);
    }
}
