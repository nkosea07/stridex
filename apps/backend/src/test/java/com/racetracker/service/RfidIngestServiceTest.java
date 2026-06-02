package com.racetracker.service;

import com.racetracker.dto.rfid.RfidReadEventBatchRequest;
import com.racetracker.dto.rfid.RfidReadEventBatchResponse;
import com.racetracker.dto.rfid.RfidReadEventRequest;
import com.racetracker.exception.UnauthorizedException;
import com.racetracker.model.*;
import com.racetracker.repository.CheckpointRecordRepository;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.RfidReadEventRepository;
import com.racetracker.repository.RfidReaderRepository;
import com.racetracker.service.impl.RfidIngestServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RfidIngestServiceTest {

    @Mock
    private RfidReaderRepository rfidReaderRepository;

    @Mock
    private RfidReadEventRepository rfidReadEventRepository;

    @Mock
    private ParticipantRepository participantRepository;

    @Mock
    private CheckpointRecordRepository checkpointRecordRepository;

    @Mock
    private RfidApiKeyService rfidApiKeyService;

    private RfidIngestServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RfidIngestServiceImpl(
            rfidReaderRepository,
            rfidReadEventRepository,
            participantRepository,
            checkpointRecordRepository,
            rfidApiKeyService
        );
    }

    @Test
    void ingestCreatesCheckpointRecordForAcceptedRead() {
        Fixture fixture = fixture(RfidReader.RfidReaderStatus.ACTIVE);
        Participant participant = participant(fixture.race.getId(), "TAG-1");
        CheckpointRecord savedRecord = new CheckpointRecord();
        savedRecord.setId(UUID.randomUUID());

        stubReader(fixture.reader);
        when(rfidReadEventRepository.findByReaderIdAndSourceEventId(fixture.reader.getId(), "event-1")).thenReturn(Optional.empty());
        when(participantRepository.findByRaceIdAndRfidTag(fixture.race.getId(), "TAG-1")).thenReturn(Optional.of(participant));
        when(checkpointRecordRepository.findByParticipantIdAndCheckpointId(participant.getId(), fixture.checkpoint.getId())).thenReturn(Optional.empty());
        when(checkpointRecordRepository.save(any(CheckpointRecord.class))).thenReturn(savedRecord);

        RfidReadEventBatchResponse response = service.ingest("api-key", batch(read("event-1", "TAG-1")), "127.0.0.1");

        assertThat(response.getAccepted()).isEqualTo(1);
        assertThat(response.getResults()).singleElement().satisfies(result -> {
            assertThat(result.getStatus()).isEqualTo("ACCEPTED");
            assertThat(result.getParticipantId()).isEqualTo(participant.getId());
            assertThat(result.getCheckpointRecordId()).isEqualTo(savedRecord.getId());
        });

        ArgumentCaptor<CheckpointRecord> recordCaptor = ArgumentCaptor.forClass(CheckpointRecord.class);
        verify(checkpointRecordRepository).save(recordCaptor.capture());
        assertThat(recordCaptor.getValue().getRecordMethod()).isEqualTo(CheckpointRecord.RecordMethod.RFID);
        assertThat(recordCaptor.getValue().getIsVerified()).isTrue();
        assertThat(recordCaptor.getValue().getRecordedBy()).isNull();
    }

    @Test
    void ingestStoresUnknownTagWithoutCreatingCheckpointRecord() {
        Fixture fixture = fixture(RfidReader.RfidReaderStatus.ACTIVE);
        stubReader(fixture.reader);
        when(rfidReadEventRepository.findByReaderIdAndSourceEventId(fixture.reader.getId(), "event-1")).thenReturn(Optional.empty());
        when(participantRepository.findByRaceIdAndRfidTag(fixture.race.getId(), "TAG-404")).thenReturn(Optional.empty());

        RfidReadEventBatchResponse response = service.ingest("api-key", batch(read("event-1", "TAG-404")), "127.0.0.1");

        assertThat(response.getUnknownTag()).isEqualTo(1);
        assertThat(response.getResults().get(0).getStatus()).isEqualTo("UNKNOWN_TAG");
        verify(checkpointRecordRepository, never()).save(any());
    }

    @Test
    void ingestMarksExistingCheckpointRecordAsDuplicate() {
        Fixture fixture = fixture(RfidReader.RfidReaderStatus.ACTIVE);
        Participant participant = participant(fixture.race.getId(), "TAG-1");
        CheckpointRecord existingRecord = new CheckpointRecord();
        existingRecord.setId(UUID.randomUUID());

        stubReader(fixture.reader);
        when(rfidReadEventRepository.findByReaderIdAndSourceEventId(fixture.reader.getId(), "event-1")).thenReturn(Optional.empty());
        when(participantRepository.findByRaceIdAndRfidTag(fixture.race.getId(), "TAG-1")).thenReturn(Optional.of(participant));
        when(checkpointRecordRepository.findByParticipantIdAndCheckpointId(participant.getId(), fixture.checkpoint.getId()))
            .thenReturn(Optional.of(existingRecord));

        RfidReadEventBatchResponse response = service.ingest("api-key", batch(read("event-1", "TAG-1")), "127.0.0.1");

        assertThat(response.getDuplicate()).isEqualTo(1);
        assertThat(response.getResults().get(0).getCheckpointRecordId()).isEqualTo(existingRecord.getId());
        verify(checkpointRecordRepository, never()).save(any());
    }

    @Test
    void ingestMarksRevokedReaderAsInvalidReader() {
        Fixture fixture = fixture(RfidReader.RfidReaderStatus.REVOKED);
        stubReader(fixture.reader);

        RfidReadEventBatchResponse response = service.ingest("api-key", batch(read("event-1", "TAG-1")), "127.0.0.1");

        assertThat(response.getInvalidReader()).isEqualTo(1);
        assertThat(response.getResults().get(0).getStatus()).isEqualTo("INVALID_READER");
        verifyNoInteractions(participantRepository);
        verifyNoInteractions(checkpointRecordRepository);
    }

    @Test
    void ingestRejectsUnknownApiKey() {
        when(rfidApiKeyService.hashKey("api-key")).thenReturn("hashed");
        when(rfidReaderRepository.findByApiKeyHash("hashed")).thenReturn(Optional.empty());

        assertThrows(UnauthorizedException.class, () -> service.ingest("api-key", batch(read("event-1", "TAG-1")), "127.0.0.1"));
    }

    @Test
    void ingestReturnsMixedBatchCounts() {
        Fixture fixture = fixture(RfidReader.RfidReaderStatus.ACTIVE);
        Participant participant = participant(fixture.race.getId(), "TAG-1");
        CheckpointRecord savedRecord = new CheckpointRecord();
        savedRecord.setId(UUID.randomUUID());
        CheckpointRecord existingRecord = new CheckpointRecord();
        existingRecord.setId(UUID.randomUUID());

        stubReader(fixture.reader);
        when(rfidReadEventRepository.findByReaderIdAndSourceEventId(any(UUID.class), any(String.class))).thenReturn(Optional.empty());
        when(participantRepository.findByRaceIdAndRfidTag(fixture.race.getId(), "TAG-1")).thenReturn(Optional.of(participant));
        when(participantRepository.findByRaceIdAndRfidTag(fixture.race.getId(), "TAG-404")).thenReturn(Optional.empty());
        when(checkpointRecordRepository.findByParticipantIdAndCheckpointId(participant.getId(), fixture.checkpoint.getId()))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(existingRecord));
        when(checkpointRecordRepository.save(any(CheckpointRecord.class))).thenReturn(savedRecord);

        RfidReadEventBatchResponse response = service.ingest("api-key", batch(
            read("event-1", "TAG-1"),
            read("event-2", "TAG-404"),
            read("event-3", "TAG-1")
        ), "127.0.0.1");

        assertThat(response.getAccepted()).isEqualTo(1);
        assertThat(response.getUnknownTag()).isEqualTo(1);
        assertThat(response.getDuplicate()).isEqualTo(1);
        assertThat(response.getResults()).hasSize(3);
    }

    private void stubReader(RfidReader reader) {
        when(rfidApiKeyService.hashKey("api-key")).thenReturn("hashed");
        when(rfidReaderRepository.findByApiKeyHash("hashed")).thenReturn(Optional.of(reader));
    }

    private RfidReadEventBatchRequest batch(RfidReadEventRequest... reads) {
        RfidReadEventBatchRequest request = new RfidReadEventBatchRequest();
        request.setReads(List.of(reads));
        return request;
    }

    private RfidReadEventRequest read(String sourceEventId, String tag) {
        RfidReadEventRequest request = new RfidReadEventRequest();
        request.setSourceEventId(sourceEventId);
        request.setRfidTag(tag);
        request.setReadAt(OffsetDateTime.parse("2026-06-02T06:00:00Z"));
        return request;
    }

    private Fixture fixture(RfidReader.RfidReaderStatus status) {
        Race race = new Race();
        race.setId(UUID.randomUUID());
        Checkpoint checkpoint = new Checkpoint();
        checkpoint.setId(UUID.randomUUID());
        checkpoint.setRace(race);
        RfidReader reader = new RfidReader();
        reader.setId(UUID.randomUUID());
        reader.setRace(race);
        reader.setCheckpoint(checkpoint);
        reader.setStatus(status);
        return new Fixture(race, checkpoint, reader);
    }

    private Participant participant(UUID raceId, String tag) {
        Race race = new Race();
        race.setId(raceId);
        Participant participant = new Participant();
        participant.setId(UUID.randomUUID());
        participant.setRace(race);
        participant.setRfidTag(tag);
        return participant;
    }

    private record Fixture(Race race, Checkpoint checkpoint, RfidReader reader) {
    }
}
