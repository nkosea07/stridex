package com.racetracker.service;

import com.racetracker.config.RaceTrackerProperties;
import com.racetracker.model.Checkpoint;
import com.racetracker.model.CheckpointRecord;
import com.racetracker.model.Participant;
import com.racetracker.model.ParticipantCredential;
import com.racetracker.model.Race;
import com.racetracker.model.RfidReadEvent;
import com.racetracker.model.RfidReader;
import com.racetracker.repository.CheckpointRecordRepository;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.RfidReadEventRepository;
import com.racetracker.repository.RfidReaderRepository;
import com.racetracker.service.impl.RfidIngestMetrics;
import com.racetracker.service.impl.RfidReadEventProcessorImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RfidTimingRulesTest {

    private final RfidReadEventRepository eventRepository = mock(RfidReadEventRepository.class);
    private final CheckpointRecordRepository recordRepository = mock(CheckpointRecordRepository.class);
    private final ParticipantRepository participantRepository = mock(ParticipantRepository.class);
    private final ParticipantCredentialService credentialService = mock(ParticipantCredentialService.class);
    private final CutoffReevaluation cutoffReevaluation = mock(CutoffReevaluation.class);
    private final TrackingService trackingService = mock(TrackingService.class);
    private final CheckpointProgression progression = mock(CheckpointProgression.class);
    private RfidReadEventProcessorImpl processor;

    private final OffsetDateTime gun = OffsetDateTime.parse("2026-11-15T06:00:00Z");
    private Race race;
    private Checkpoint finish;
    private RfidReader reader;
    private Participant participant;

    @BeforeEach
    void setUp() {
        processor = new RfidReadEventProcessorImpl(eventRepository, recordRepository, participantRepository,
            mock(RfidReaderRepository.class), credentialService, mock(RfidIngestMetrics.class),
            mock(LiveSummaryCacheInvalidator.class), progression, cutoffReevaluation, trackingService,
            new RaceTrackerProperties());
        race = Race.builder().id(UUID.randomUUID()).status(Race.RaceStatus.IN_PROGRESS).startedAt(gun).build();
        finish = Checkpoint.builder().id(UUID.randomUUID()).race(race).name("Finish")
            .checkpointType(Checkpoint.CheckpointType.FINISH).selfScanToken("t").build();
        reader = new RfidReader();
        reader.setId(UUID.randomUUID());
        reader.setRace(race);
        reader.setCheckpoint(finish);
        reader.setStatus(RfidReader.RfidReaderStatus.ACTIVE);
        participant = Participant.builder().id(UUID.randomUUID()).race(race)
            .status(Participant.ParticipantStatus.STARTED).build();
        when(credentialService.resolveTimingParticipant(eq(ParticipantCredential.CredentialType.RFID_UHF_TIMING),
            any(), eq(race.getId()), any())).thenReturn(participant);
        when(recordRepository.findByParticipantIdAndRaceIdOrderByRecordedAtAsc(any(), any())).thenReturn(List.of());
    }

    private RfidReadEvent read(OffsetDateTime readAt, OffsetDateTime receivedAt) {
        RfidReadEvent event = new RfidReadEvent();
        event.setId(UUID.randomUUID());
        event.setReader(reader);
        event.setRace(race);
        event.setCheckpoint(finish);
        event.setRfidTag("E2003411B802011803954C45");
        event.setReadAt(readAt);
        event.setReceivedAt(receivedAt);
        event.setStatus(RfidReadEvent.RfidReadEventStatus.RECEIVED);
        when(eventRepository.findWithRelationsById(event.getId())).thenReturn(Optional.of(event));
        return event;
    }

    private CheckpointRecord existing(OffsetDateTime at, CheckpointRecord.RecordMethod method) {
        CheckpointRecord record = new CheckpointRecord();
        record.setId(UUID.randomUUID());
        record.setCheckpoint(finish);
        record.setParticipant(participant);
        record.setRecordedAt(at);
        record.setRecordMethod(method);
        when(recordRepository.findFirstByParticipantIdAndCheckpointIdOrderByPassNumberAsc(participant.getId(), finish.getId()))
            .thenReturn(Optional.of(record));
        return record;
    }

    @Test
    void readBeforeTheGunIsIgnoredAndCreatesNoTime() {
        RfidReadEvent event = read(gun.minusMinutes(3), gun.minusMinutes(3).plusSeconds(1));

        processor.process(event.getId());

        assertThat(event.getStatus()).isEqualTo(RfidReadEvent.RfidReadEventStatus.IGNORED);
        assertThat(event.getErrorMessage()).contains("before the race start");
        verify(recordRepository, never()).save(any());
    }

    @Test
    void scheduledStartIsTheGateWhenNoGunTimeWasRecorded() {
        race.setStartedAt(null);
        race.setRaceDate(java.time.LocalDate.of(2026, 11, 15));
        race.setStartTime(java.time.LocalTime.of(8, 0));
        race.setTimezone("Africa/Harare");
        RfidReadEvent event = read(gun.minusSeconds(30), gun);

        processor.process(event.getId());

        assertThat(event.getStatus()).isEqualTo(RfidReadEvent.RfidReadEventStatus.IGNORED);
    }

    @Test
    void readDatedInTheFutureIsIgnoredAsAReaderClockFault() {
        OffsetDateTime received = gun.plusHours(1);
        RfidReadEvent event = read(received.plusMinutes(10), received);

        processor.process(event.getId());

        assertThat(event.getStatus()).isEqualTo(RfidReadEvent.RfidReadEventStatus.IGNORED);
        assertThat(event.getErrorMessage()).contains("clock");
        verify(recordRepository, never()).save(any());
    }

    @Test
    void firstReadCreatesTheTimeAndPushesALiveUpdate() {
        RfidReadEvent event = read(gun.plusHours(3), gun.plusHours(3).plusSeconds(2));
        when(recordRepository.findFirstByParticipantIdAndCheckpointIdOrderByPassNumberAsc(participant.getId(), finish.getId()))
            .thenReturn(Optional.empty());
        when(recordRepository.save(any(CheckpointRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));

        processor.process(event.getId());

        assertThat(event.getStatus()).isEqualTo(RfidReadEvent.RfidReadEventStatus.ACCEPTED);
        verify(progression).apply(participant, finish, false);
        verify(trackingService).publishTimingUpdate(race.getId(), participant.getId());
    }

    @Test
    void earlierReadProcessedLateReplacesTheAutomaticTimeAndReappliesCutoffs() {
        CheckpointRecord record = existing(gun.plusHours(3).plusSeconds(40), CheckpointRecord.RecordMethod.RFID);
        RfidReadEvent earlier = read(gun.plusHours(3), gun.plusHours(3).plusMinutes(5));

        processor.process(earlier.getId());

        assertThat(record.getRecordedAt()).isEqualTo(gun.plusHours(3));
        assertThat(earlier.getStatus()).isEqualTo(RfidReadEvent.RfidReadEventStatus.ACCEPTED);
        verify(cutoffReevaluation).reevaluate(participant, race.getId());
        verify(trackingService).publishTimingUpdate(race.getId(), participant.getId());
    }

    @Test
    void laterReadIsADuplicate() {
        CheckpointRecord record = existing(gun.plusHours(3), CheckpointRecord.RecordMethod.RFID);
        RfidReadEvent later = read(gun.plusHours(3).plusSeconds(20), gun.plusHours(3).plusSeconds(21));

        processor.process(later.getId());

        assertThat(record.getRecordedAt()).isEqualTo(gun.plusHours(3));
        assertThat(later.getStatus()).isEqualTo(RfidReadEvent.RfidReadEventStatus.DUPLICATE);
        verify(cutoffReevaluation, never()).reevaluate(any(), any());
    }

    @Test
    void officialCorrectionsAndManualTimesAreNeverOverwrittenByReads() {
        CheckpointRecord corrected = existing(gun.plusHours(3).plusSeconds(40), CheckpointRecord.RecordMethod.RFID);
        corrected.setAdjustedRecordedAt(gun.plusHours(3).plusSeconds(35));
        RfidReadEvent earlier = read(gun.plusHours(3), gun.plusHours(3).plusSeconds(50));

        processor.process(earlier.getId());

        assertThat(corrected.getRecordedAt()).isEqualTo(gun.plusHours(3).plusSeconds(40));
        assertThat(earlier.getStatus()).isEqualTo(RfidReadEvent.RfidReadEventStatus.DUPLICATE);

        CheckpointRecord manual = existing(gun.plusHours(3).plusSeconds(40), CheckpointRecord.RecordMethod.MANUAL);
        RfidReadEvent another = read(gun.plusHours(3), gun.plusHours(3).plusSeconds(55));
        processor.process(another.getId());
        assertThat(manual.getRecordedAt()).isEqualTo(gun.plusHours(3).plusSeconds(40));
    }
}
