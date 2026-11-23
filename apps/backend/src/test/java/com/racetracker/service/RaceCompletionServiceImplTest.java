package com.racetracker.service;

import com.racetracker.model.Checkpoint;
import com.racetracker.model.Participant;
import com.racetracker.model.Race;
import com.racetracker.repository.CheckpointRepository;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.RaceStageRepository;
import com.racetracker.repository.RaceRepository;
import com.racetracker.service.impl.RaceCompletionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RaceCompletionServiceImplTest {

    @Mock private RaceRepository raceRepository;
    @Mock private ParticipantRepository participantRepository;
    @Mock private CheckpointRepository checkpointRepository;
    @Mock private RaceStageRepository raceStageRepository;
    @Mock private com.racetracker.service.AuditEventService auditEventService;

    private RaceCompletionServiceImpl service;
    private final UUID raceId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new RaceCompletionServiceImpl(raceRepository, participantRepository,
            checkpointRepository, raceStageRepository, auditEventService, 24, 60);
    }

    @Test
    void completeRace_marksCompletedAndResolvesStartersAsDnf() {
        Race race = new Race();
        race.setId(raceId);
        race.setStatus(Race.RaceStatus.IN_PROGRESS);
        Participant starter = Participant.builder().id(UUID.randomUUID())
            .status(Participant.ParticipantStatus.STARTED).build();

        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
        when(participantRepository.findByRaceIdAndStatusOrderByRegisteredAtAsc(
            raceId, Participant.ParticipantStatus.STARTED)).thenReturn(List.of(starter));

        Race completed = service.completeRace(raceId);

        assertThat(completed.getStatus()).isEqualTo(Race.RaceStatus.COMPLETED);
        assertThat(completed.getCompletedAt()).isNotNull();
        assertThat(starter.getStatus()).isEqualTo(Participant.ParticipantStatus.DNF);
        assertThat(starter.getStatusReason()).isEqualTo("Did not finish before the race was completed");
        verify(raceRepository).save(race);
        verify(participantRepository).saveAll(List.of(starter));
    }

    @Test
    void completeRace_marksEntrantsWhoNeverStartedAsDnsAndAuditsTheResolution() {
        Race race = new Race();
        race.setId(raceId);
        race.setStatus(Race.RaceStatus.IN_PROGRESS);
        Participant starter = Participant.builder().id(UUID.randomUUID())
            .status(Participant.ParticipantStatus.STARTED).build();
        Participant noShow = Participant.builder().id(UUID.randomUUID())
            .status(Participant.ParticipantStatus.CHECKED_IN).build();
        Participant unconfirmed = Participant.builder().id(UUID.randomUUID())
            .status(Participant.ParticipantStatus.REGISTERED).build();

        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
        when(participantRepository.findByRaceIdAndStatusOrderByRegisteredAtAsc(
            raceId, Participant.ParticipantStatus.STARTED)).thenReturn(List.of(starter));
        when(participantRepository.findByRaceIdAndStatusInOrderByRegisteredAtAsc(eq(raceId), any()))
            .thenAnswer(invocation -> {
                java.util.Collection<Participant.ParticipantStatus> statuses = invocation.getArgument(1);
                assertThat(statuses).containsExactlyInAnyOrder(Participant.ParticipantStatus.REGISTERED,
                    Participant.ParticipantStatus.CONFIRMED, Participant.ParticipantStatus.CHECKED_IN);
                return List.of(noShow, unconfirmed);
            });

        service.completeRace(raceId);

        assertThat(noShow.getStatus()).isEqualTo(Participant.ParticipantStatus.DNS);
        assertThat(unconfirmed.getStatus()).isEqualTo(Participant.ParticipantStatus.DNS);
        assertThat(noShow.getStatusReason()).isEqualTo("Did not start before the race was completed");
        verify(participantRepository).saveAll(List.of(starter, noShow, unconfirmed));
        verify(auditEventService).recordForActor(eq("RACE_COMPLETION_STATUSES_RESOLVED"), any(), eq("RACE"),
            eq(raceId.toString()), eq("SUCCESS"), eq(java.util.Map.of("dnf", 1, "dns", 2)));
    }

    @Test
    void completeRace_isIdempotentForAlreadyCompletedRace() {
        Race race = new Race();
        race.setId(raceId);
        race.setStatus(Race.RaceStatus.COMPLETED);
        race.setCompletedAt(java.time.OffsetDateTime.now());

        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
        when(participantRepository.findByRaceIdAndStatusOrderByRegisteredAtAsc(
            raceId, Participant.ParticipantStatus.STARTED)).thenReturn(List.of());

        service.completeRace(raceId);

        verify(raceRepository, never()).save(any(Race.class));
        verify(participantRepository, never()).saveAll(any());
        org.mockito.Mockito.verifyNoInteractions(auditEventService);
    }

    @Test
    void autoCompleteDueRaces_closesOnlyRacesPastTheirTimeLimit() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Race finished = inProgressRace(raceId, now.minusHours(8));
        UUID runningId = UUID.randomUUID();
        Race stillRunning = inProgressRace(runningId, now.minusHours(3));

        when(raceRepository.findByStatusAndRaceDateLessThanEqual(eq(Race.RaceStatus.IN_PROGRESS), any(LocalDate.class)))
            .thenReturn(List.of(finished, stillRunning));
        when(raceStageRepository.findByRaceIdOrderBySequenceAsc(any())).thenReturn(List.of());
        when(checkpointRepository.findByRaceIdOrderByOrderIndexAsc(any())).thenReturn(List.of(finishWithCutoff(Duration.ofHours(6))));
        when(participantRepository.findByRaceIdAndStatusOrderByRegisteredAtAsc(
            eq(raceId), eq(Participant.ParticipantStatus.STARTED))).thenReturn(List.of());

        int completed = service.autoCompleteDueRaces();

        assertThat(completed).isEqualTo(1);
        assertThat(finished.getStatus()).isEqualTo(Race.RaceStatus.COMPLETED);
        assertThat(stillRunning.getStatus()).isEqualTo(Race.RaceStatus.IN_PROGRESS);
        verify(participantRepository, never())
            .findByRaceIdAndStatusOrderByRegisteredAtAsc(runningId, Participant.ParticipantStatus.STARTED);
    }

    private Race inProgressRace(UUID id, OffsetDateTime scheduledStartUtc) {
        Race race = new Race();
        race.setId(id);
        race.setStatus(Race.RaceStatus.IN_PROGRESS);
        race.setTimezone("UTC");
        race.setRaceDate(scheduledStartUtc.toLocalDate());
        race.setStartTime(scheduledStartUtc.toLocalTime());
        return race;
    }

    private Checkpoint finishWithCutoff(Duration cutoff) {
        Checkpoint checkpoint = new Checkpoint();
        checkpoint.setCheckpointType(Checkpoint.CheckpointType.FINISH);
        checkpoint.setCutoffTime(cutoff);
        return checkpoint;
    }
}
