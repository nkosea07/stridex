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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LapTimingTest {

    private final OffsetDateTime gun = OffsetDateTime.parse("2026-11-15T06:00:00Z");
    private final RfidReadEventRepository events = mock(RfidReadEventRepository.class);
    private final CheckpointRecordRepository records = mock(CheckpointRecordRepository.class);
    private final List<CheckpointRecord> stored = new ArrayList<>();
    private final List<RfidReadEvent> processed = new ArrayList<>();
    private RfidReadEventProcessorImpl processor;
    private Race race;
    private Checkpoint finishLine;
    private RfidReader reader;
    private Participant runner;

    @BeforeEach
    void setUp() {
        ParticipantCredentialService credentials = mock(ParticipantCredentialService.class);
        ParticipantRepository participants = mock(ParticipantRepository.class);
        processor = new RfidReadEventProcessorImpl(events, records, participants, mock(RfidReaderRepository.class),
            credentials, mock(RfidIngestMetrics.class), mock(LiveSummaryCacheInvalidator.class),
            new CheckpointProgression(mock(SpectatorAlertService.class)),
            new CutoffReevaluation(records, participants), mock(TrackingService.class), new RaceTrackerProperties());
        race = Race.builder().id(UUID.randomUUID()).status(Race.RaceStatus.IN_PROGRESS).startedAt(gun).build();
        finishLine = Checkpoint.builder().id(UUID.randomUUID()).race(race).name("Lap line")
            .checkpointType(Checkpoint.CheckpointType.FINISH).expectedPasses(3).minPassSeconds(120)
            .firstPassIsStart(false).selfScanToken("t").build();
        reader = new RfidReader();
        reader.setId(UUID.randomUUID());
        reader.setRace(race);
        reader.setCheckpoint(finishLine);
        reader.setStatus(RfidReader.RfidReaderStatus.ACTIVE);
        runner = Participant.builder().id(UUID.randomUUID()).race(race).status(Participant.ParticipantStatus.STARTED).build();
        when(credentials.resolveTimingParticipant(eq(ParticipantCredential.CredentialType.RFID_UHF_TIMING), any(),
            eq(race.getId()), any())).thenReturn(runner);
        when(records.findByParticipantIdAndCheckpointIdOrderByPassNumberAsc(runner.getId(), finishLine.getId()))
            .thenAnswer(invocation -> new ArrayList<>(stored));
        when(records.findByParticipantIdAndRaceIdOrderByRecordedAtAsc(any(), any())).thenAnswer(invocation -> new ArrayList<>(stored));
        when(records.save(any(CheckpointRecord.class))).thenAnswer(invocation -> {
            CheckpointRecord record = invocation.getArgument(0);
            if (!stored.contains(record)) {
                stored.add(record);
            }
            return record;
        });
        when(events.findByMatchedParticipantIdAndCheckpointIdAndStatusInOrderByReadAtAsc(eq(runner.getId()),
            eq(finishLine.getId()), any())).thenAnswer(invocation -> new ArrayList<>(processed));
    }

    private void read(int minutes, int seconds) {
        RfidReadEvent event = new RfidReadEvent();
        event.setId(UUID.randomUUID());
        event.setReader(reader);
        event.setRace(race);
        event.setCheckpoint(finishLine);
        event.setRfidTag("E200AA");
        event.setReadAt(gun.plusMinutes(minutes).plusSeconds(seconds));
        event.setReceivedAt(gun.plusMinutes(minutes).plusSeconds(seconds + 1));
        event.setStatus(RfidReadEvent.RfidReadEventStatus.RECEIVED);
        when(events.findWithRelationsById(event.getId())).thenReturn(Optional.of(event));
        processor.process(event.getId());
        processed.add(event);
    }

    private List<OffsetDateTime> passTimes() {
        return stored.stream().sorted(java.util.Comparator.comparing(CheckpointPasses::pass))
            .map(CheckpointRecord::getRecordedAt).toList();
    }

    @Test
    void eachLapIsItsOwnPassAndOnlyTheFinalLapFinishes() {
        read(20, 0);
        read(20, 3);
        assertThat(stored).hasSize(1);
        assertThat(runner.getStatus()).isEqualTo(Participant.ParticipantStatus.STARTED);

        read(41, 10);
        read(62, 5);

        assertThat(passTimes()).containsExactly(gun.plusMinutes(20), gun.plusMinutes(41).plusSeconds(10),
            gun.plusMinutes(62).plusSeconds(5));
        assertThat(runner.getStatus()).isEqualTo(Participant.ParticipantStatus.FINISHED);
    }

    @Test
    void lapReadsArrivingOutOfOrderStillProduceTheRightPasses() {
        read(41, 10);
        read(20, 0);

        assertThat(passTimes()).containsExactly(gun.plusMinutes(20), gun.plusMinutes(41).plusSeconds(10));
        assertThat(runner.getStatus()).isEqualTo(Participant.ParticipantStatus.STARTED);
    }

    @Test
    void sharedStartFinishMatUsesTheFirstPassAsTheChipStart() {
        finishLine.setFirstPassIsStart(true);
        CheckpointRecord first = new CheckpointRecord();
        first.setCheckpoint(finishLine);
        first.setPassNumber(1);
        first.setRecordedAt(gun.plusSeconds(25));
        CheckpointRecord last = new CheckpointRecord();
        last.setCheckpoint(finishLine);
        last.setPassNumber(3);
        last.setRecordedAt(gun.plusMinutes(62));

        assertThat(CheckpointCutoff.resolveStart(runner, List.of(first, last))).isEqualTo(gun.plusSeconds(25));
        assertThat(CheckpointPasses.isFinish(first)).isFalse();
        assertThat(CheckpointPasses.isFinish(last)).isTrue();
    }
}
