package com.racetracker.service;

import com.racetracker.model.*;
import com.racetracker.repository.CheckpointRecordRepository;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.RfidReadEventRepository;
import com.racetracker.repository.RfidReaderRepository;
import com.racetracker.service.impl.RfidIngestMetrics;
import com.racetracker.service.impl.RfidReadEventProcessorImpl;
import com.racetracker.service.LiveSummaryCacheInvalidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RfidReadEventProcessorTest {

    @Mock
    private RfidReadEventRepository rfidReadEventRepository;

    @Mock
    private CheckpointRecordRepository checkpointRecordRepository;

    @Mock
    private ParticipantRepository participantRepository;

    @Mock
    private RfidReaderRepository rfidReaderRepository;

    @Mock
    private ParticipantCredentialService participantCredentialService;

    @Mock
    private RfidIngestMetrics rfidIngestMetrics;

    @Mock
    private LiveSummaryCacheInvalidator liveSummaryCacheInvalidator;

    @Mock
    private SpectatorAlertService spectatorAlertService;

    private RfidReadEventProcessorImpl service;

    @BeforeEach
    void setUp() {
        service = new RfidReadEventProcessorImpl(
            rfidReadEventRepository,
            checkpointRecordRepository,
            participantRepository,
            rfidReaderRepository,
            participantCredentialService,
            rfidIngestMetrics,
            liveSummaryCacheInvalidator,
            new CheckpointProgression(spectatorAlertService)
        );
    }

    @Test
    void processAcceptsResolvedCredentialAndCreatesCheckpointRecord() {
        Fixture fixture = fixture();
        Participant participant = new Participant();
        participant.setId(UUID.randomUUID());
        participant.setStatus(Participant.ParticipantStatus.CHECKED_IN);

        when(rfidReadEventRepository.findWithRelationsById(fixture.event.getId())).thenReturn(Optional.of(fixture.event));
        when(rfidReadEventRepository.findFirstByReaderIdAndSourceEventIdAndIdNotOrderByReceivedAtAscIdAsc(
            fixture.reader.getId(),
            "event-1",
            fixture.event.getId()
        )).thenReturn(Optional.empty());
        when(participantCredentialService.resolveTimingParticipant(
            ParticipantCredential.CredentialType.RFID_UHF_TIMING,
            null,
            fixture.reader.getRace().getId(),
            "TAG-1"
        )).thenReturn(participant);
        when(checkpointRecordRepository.findByParticipantIdAndCheckpointId(participant.getId(), fixture.reader.getCheckpoint().getId()))
            .thenReturn(Optional.empty());
        when(checkpointRecordRepository.save(any(CheckpointRecord.class))).thenAnswer(invocation -> {
            CheckpointRecord record = invocation.getArgument(0);
            record.setId(UUID.randomUUID());
            return record;
        });

        service.process(fixture.event.getId());

        assertThat(fixture.event.getStatus()).isEqualTo(RfidReadEvent.RfidReadEventStatus.ACCEPTED);
        assertThat(fixture.event.getMatchedParticipant()).isEqualTo(participant);
        assertThat(participant.getStatus()).isEqualTo(Participant.ParticipantStatus.STARTED);
        verify(rfidReaderRepository).save(fixture.reader);
        verify(liveSummaryCacheInvalidator).evictLastCheckpointAfterCommit(
            participant.getId(), fixture.reader.getRace().getId());
    }

    @Test
    void processMarksUnknownCredential() {
        Fixture fixture = fixture();
        when(rfidReadEventRepository.findWithRelationsById(fixture.event.getId())).thenReturn(Optional.of(fixture.event));
        when(rfidReadEventRepository.findFirstByReaderIdAndSourceEventIdAndIdNotOrderByReceivedAtAscIdAsc(
            fixture.reader.getId(),
            "event-1",
            fixture.event.getId()
        )).thenReturn(Optional.empty());
        when(participantCredentialService.resolveTimingParticipant(
            ParticipantCredential.CredentialType.RFID_UHF_TIMING,
            null,
            fixture.reader.getRace().getId(),
            "TAG-1"
        )).thenReturn(null);

        service.process(fixture.event.getId());

        assertThat(fixture.event.getStatus()).isEqualTo(RfidReadEvent.RfidReadEventStatus.UNKNOWN_TAG);
        verify(rfidIngestMetrics).incrementUnknownCredential();
    }

    private Fixture fixture() {
        Race race = new Race();
        race.setId(UUID.randomUUID());
        Checkpoint checkpoint = new Checkpoint();
        checkpoint.setId(UUID.randomUUID());
        checkpoint.setRace(race);
        checkpoint.setCheckpointType(Checkpoint.CheckpointType.START);
        RfidReader reader = new RfidReader();
        reader.setId(UUID.randomUUID());
        reader.setRace(race);
        reader.setCheckpoint(checkpoint);
        reader.setStatus(RfidReader.RfidReaderStatus.ACTIVE);
        RfidReadEvent event = new RfidReadEvent();
        event.setId(UUID.randomUUID());
        event.setReader(reader);
        event.setRace(race);
        event.setCheckpoint(checkpoint);
        event.setRfidTag("TAG-1");
        event.setSourceEventId("event-1");
        event.setReadAt(OffsetDateTime.parse("2026-06-02T06:00:00Z"));
        event.setReceivedAt(OffsetDateTime.parse("2026-06-02T06:00:01Z"));
        event.setStatus(RfidReadEvent.RfidReadEventStatus.RECEIVED);
        return new Fixture(reader, event);
    }

    private record Fixture(RfidReader reader, RfidReadEvent event) {
    }

    @Test
    void failedProcessingBacksOffThenMarksTheEventErrorAfterTheAttemptLimit() {
        UUID eventId = UUID.randomUUID();
        RfidReadEvent event = new RfidReadEvent();
        event.setId(eventId);
        event.setStatus(RfidReadEvent.RfidReadEventStatus.RECEIVED);
        event.setProcessingAttempts(0);
        org.mockito.Mockito.when(rfidReadEventRepository.findById(eventId)).thenReturn(java.util.Optional.of(event));

        boolean exhausted = service.recordProcessingFailure(eventId, new IllegalStateException("db"));

        org.assertj.core.api.Assertions.assertThat(exhausted).isFalse();
        org.assertj.core.api.Assertions.assertThat(event.getProcessingAttempts()).isEqualTo(1);
        org.assertj.core.api.Assertions.assertThat(event.getNextAttemptAt()).isAfter(java.time.OffsetDateTime.now());
        org.assertj.core.api.Assertions.assertThat(event.getStatus()).isEqualTo(RfidReadEvent.RfidReadEventStatus.RECEIVED);

        event.setProcessingAttempts(7);
        exhausted = service.recordProcessingFailure(eventId, new IllegalStateException("db"));

        org.assertj.core.api.Assertions.assertThat(exhausted).isTrue();
        org.assertj.core.api.Assertions.assertThat(event.getStatus()).isEqualTo(RfidReadEvent.RfidReadEventStatus.ERROR);
        org.assertj.core.api.Assertions.assertThat(event.getNextAttemptAt()).isNull();
        org.assertj.core.api.Assertions.assertThat(event.getErrorMessage()).contains("8 attempts").contains("IllegalStateException");
    }
}
