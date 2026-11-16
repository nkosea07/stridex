package com.racetracker.service;

import com.racetracker.dto.race.WaveStartRequest;
import com.racetracker.exception.ApiException;
import com.racetracker.exception.ConflictException;
import com.racetracker.model.Checkpoint;
import com.racetracker.model.CheckpointRecord;
import com.racetracker.model.Participant;
import com.racetracker.model.Race;
import com.racetracker.model.RaceWave;
import com.racetracker.model.RfidReadEvent;
import com.racetracker.repository.CheckpointRecordRepository;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.RaceRepository;
import com.racetracker.repository.RaceWaveRepository;
import com.racetracker.repository.RfidReadEventRepository;
import com.racetracker.repository.UserRepository;
import com.racetracker.security.RaceAccessGuard;
import com.racetracker.service.impl.WaveServiceImpl;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WaveTimingTest {

    private final OffsetDateTime scheduled = OffsetDateTime.parse("2026-11-15T06:00:00Z");

    private Race race() {
        return Race.builder().id(UUID.randomUUID()).raceDate(LocalDate.of(2026, 11, 15))
            .startTime(LocalTime.of(6, 0)).timezone("UTC").status(Race.RaceStatus.IN_PROGRESS).build();
    }

    @Test
    void gunStartPrefersTheWaveGunThenWaveScheduleThenRaceGunThenSchedule() {
        Race race = race();
        Participant participant = Participant.builder().race(race).build();
        assertThat(CheckpointCutoff.gunStart(participant)).isEqualTo(scheduled);

        race.setStartedAt(scheduled.plusMinutes(4));
        assertThat(CheckpointCutoff.gunStart(participant)).isEqualTo(scheduled.plusMinutes(4));

        RaceWave wave = RaceWave.builder().race(race).name("B").scheduledStart(scheduled.plusMinutes(10)).build();
        participant.setWave(wave);
        assertThat(CheckpointCutoff.gunStart(participant)).isEqualTo(scheduled.plusMinutes(10));

        wave.setStartedAt(scheduled.plusMinutes(12));
        assertThat(CheckpointCutoff.gunStart(participant)).isEqualTo(scheduled.plusMinutes(12));
        assertThat(CheckpointCutoff.resolveStart(participant, List.of())).isEqualTo(scheduled.plusMinutes(12));
    }

    @Test
    void regateMovesEarlyTimesToTheFirstPostGunReadOrRemovesThem() {
        CheckpointRecordRepository records = mock(CheckpointRecordRepository.class);
        RfidReadEventRepository events = mock(RfidReadEventRepository.class);
        CutoffReevaluation cutoffs = mock(CutoffReevaluation.class);
        TimingRegate regate = new TimingRegate(records, events, cutoffs, mock(LiveSummaryCacheInvalidator.class));
        Race race = race();
        UUID waveId = UUID.randomUUID();
        OffsetDateTime gun = scheduled.plusMinutes(12);
        Participant movedAthlete = Participant.builder().id(UUID.randomUUID()).race(race).build();
        Participant removedAthlete = Participant.builder().id(UUID.randomUUID()).race(race).build();
        Checkpoint start = Checkpoint.builder().id(UUID.randomUUID()).checkpointType(Checkpoint.CheckpointType.START).selfScanToken("t").build();
        CheckpointRecord early = record(movedAthlete, start, gun.minusMinutes(3));
        CheckpointRecord orphan = record(removedAthlete, start, gun.minusMinutes(1));
        when(records.findAutomaticBeforeGateInWave(race.getId(), waveId, gun)).thenReturn(List.of(early, orphan));
        RfidReadEvent postGun = new RfidReadEvent();
        postGun.setReadAt(gun.plusSeconds(41));
        when(events.findFirstByMatchedParticipantIdAndCheckpointIdAndReadAtGreaterThanEqualAndStatusInOrderByReadAtAsc(
            eq(movedAthlete.getId()), eq(start.getId()), eq(gun), any())).thenReturn(Optional.of(postGun));
        when(events.requeueByRaceIdAndStatusIn(eq(race.getId()), any())).thenReturn(7);

        TimingRegate.Outcome outcome = regate.apply(race.getId(), waveId, gun);

        assertThat(outcome).isEqualTo(new TimingRegate.Outcome(1, 1, 7));
        assertThat(early.getRecordedAt()).isEqualTo(gun.plusSeconds(41));
        verify(records).delete(orphan);
        verify(cutoffs).reevaluate(movedAthlete, race.getId());
        verify(cutoffs).reevaluate(removedAthlete, race.getId());
    }

    private CheckpointRecord record(Participant participant, Checkpoint checkpoint, OffsetDateTime at) {
        CheckpointRecord record = new CheckpointRecord();
        record.setParticipant(participant);
        record.setCheckpoint(checkpoint);
        record.setRecordedAt(at);
        record.setRecordMethod(CheckpointRecord.RecordMethod.RFID);
        return record;
    }

    @Test
    void startingAWaveRecordsTheGunRegatesAndAuditsAndCorrectionsNeedAReason() {
        RaceWaveRepository waves = mock(RaceWaveRepository.class);
        RaceRepository races = mock(RaceRepository.class);
        TimingRegate regate = mock(TimingRegate.class);
        AuditEventService audit = mock(AuditEventService.class);
        RaceAccessGuard guard = mock(RaceAccessGuard.class);
        when(guard.canManage(any())).thenReturn(true);
        WaveServiceImpl service = new WaveServiceImpl(waves, races, mock(ParticipantRepository.class), mock(UserRepository.class),
            guard, new ResultLock(races), regate, audit);
        Race race = race();
        RaceWave wave = RaceWave.builder().id(UUID.randomUUID()).race(race).name("A").build();
        when(waves.findByRaceIdAndId(race.getId(), wave.getId())).thenReturn(Optional.of(wave));
        when(waves.save(any(RaceWave.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(regate.apply(any(), any(), any())).thenReturn(new TimingRegate.Outcome(0, 0, 0));

        service.start(race.getId(), wave.getId(), null);

        assertThat(wave.getStartedAt()).isNotNull();
        verify(regate).apply(race.getId(), wave.getId(), wave.getStartedAt());
        verify(audit).recordInCurrentTransaction(eq("RACE_WAVE_STARTED"), any(), eq("RACE_WAVE"), any(), eq("SUCCESS"), anyMap());

        WaveStartRequest correction = new WaveStartRequest();
        correction.setStartedAt(scheduled.plusMinutes(1));
        assertThatThrownBy(() -> service.start(race.getId(), wave.getId(), correction))
            .isInstanceOf(ApiException.class).extracting("code").isEqualTo("GUN_TIME_REASON_REQUIRED");

        race.setResultsCertifiedAt(OffsetDateTime.now());
        correction.setReason("Starter's log shows 06:01");
        assertThatThrownBy(() -> service.start(race.getId(), wave.getId(), correction))
            .isInstanceOf(ConflictException.class).extracting("code").isEqualTo(ResultLock.RESULTS_CERTIFIED);
        verify(regate, never()).apply(race.getId(), wave.getId(), scheduled.plusMinutes(1));
    }

    @Test
    void assignedOfficialsCanFireAnUnstartedWaveButNotBackdateOrCorrectIt() {
        RaceWaveRepository waves = mock(RaceWaveRepository.class);
        RaceRepository races = mock(RaceRepository.class);
        TimingRegate regate = mock(TimingRegate.class);
        RaceAccessGuard guard = mock(RaceAccessGuard.class);
        when(guard.canManage(any())).thenReturn(false);
        WaveServiceImpl service = new WaveServiceImpl(waves, races, mock(ParticipantRepository.class), mock(UserRepository.class),
            guard, new ResultLock(races), regate, mock(AuditEventService.class));
        Race race = race();
        RaceWave wave = RaceWave.builder().id(UUID.randomUUID()).race(race).name("Elite").build();
        when(waves.findByRaceIdAndId(race.getId(), wave.getId())).thenReturn(Optional.of(wave));
        when(waves.save(any(RaceWave.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(regate.apply(any(), any(), any())).thenReturn(new TimingRegate.Outcome(0, 0, 0));

        WaveStartRequest backdated = new WaveStartRequest();
        backdated.setStartedAt(OffsetDateTime.now().minusMinutes(2));
        assertThatThrownBy(() -> service.start(race.getId(), wave.getId(), backdated))
            .isInstanceOf(ApiException.class).extracting("code").isEqualTo("GUN_TIME_OFFICIAL_NOW_ONLY");

        service.start(race.getId(), wave.getId(), null);
        verify(guard, org.mockito.Mockito.atLeastOnce()).assertCanPerformOperationalAction(race.getId());
        assertThat(wave.getStartedAt()).isNotNull();

        WaveStartRequest correction = new WaveStartRequest();
        correction.setReason("Starter's log shows a later gun");
        assertThatThrownBy(() -> service.start(race.getId(), wave.getId(), correction))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
}
