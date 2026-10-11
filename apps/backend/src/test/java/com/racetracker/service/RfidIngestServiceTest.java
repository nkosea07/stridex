package com.racetracker.service;

import com.racetracker.dto.rfid.RfidReadEventBatchRequest;
import com.racetracker.dto.rfid.RfidReadEventBatchResponse;
import com.racetracker.dto.rfid.RfidReadEventRequest;
import com.racetracker.config.RaceTrackerProperties;
import com.racetracker.exception.UnauthorizedException;
import com.racetracker.model.*;
import com.racetracker.repository.RfidReadEventRepository;
import com.racetracker.repository.RfidReaderRepository;
import com.racetracker.service.impl.RfidIngestServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RfidIngestServiceTest {

    @Mock
    private RfidReaderRepository rfidReaderRepository;

    @Mock
    private RfidReadEventRepository rfidReadEventRepository;

    @Mock
    private RfidApiKeyService rfidApiKeyService;

    @Mock
    private RfidEventPublisher rfidEventPublisher;

    private RfidIngestServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RfidIngestServiceImpl(
            rfidReaderRepository,
            rfidReadEventRepository,
            rfidApiKeyService,
            rfidEventPublisher,
            new RaceTrackerProperties()
        );
    }

    @Test
    void ingestQueuesAcceptedReadForAsyncProcessing() {
        Fixture fixture = fixture(RfidReader.RfidReaderStatus.ACTIVE);

        stubReader(fixture.reader);

        RfidReadEventBatchResponse response = service.ingest("api-key", batch(read("event-1", "TAG-1")), "127.0.0.1");

        assertThat(response.getAccepted()).isEqualTo(1);
        assertThat(response.getResults()).singleElement().satisfies(result -> {
            assertThat(result.getStatus()).isEqualTo("ACCEPTED");
            assertThat(result.getParticipantId()).isNull();
            assertThat(result.getCheckpointRecordId()).isNull();
        });
        verify(rfidEventPublisher).publish(any(RfidReadEvent.class));
    }

    @Test
    void ingestMarksRevokedReaderAsInvalidReader() {
        Fixture fixture = fixture(RfidReader.RfidReaderStatus.REVOKED);
        stubReader(fixture.reader);

        RfidReadEventBatchResponse response = service.ingest("api-key", batch(read("event-1", "TAG-1")), "127.0.0.1");

        assertThat(response.getInvalidReader()).isEqualTo(1);
        assertThat(response.getResults().get(0).getStatus()).isEqualTo("INVALID_READER");
        verify(rfidEventPublisher, never()).publish(any(RfidReadEvent.class));
    }

    @Test
    void ingestReturnsDuplicateWithoutPublishingAnAlreadySeenSourceEvent() {
        Fixture fixture = fixture(RfidReader.RfidReaderStatus.ACTIVE);
        when(rfidApiKeyService.hashKey("api-key")).thenReturn("hashed");
        when(rfidReaderRepository.findByApiKeyHash("hashed")).thenReturn(Optional.of(fixture.reader));
        RfidReadEvent existing = new RfidReadEvent();
        existing.setMatchedParticipant(null);
        existing.setCheckpointRecord(null);
        when(rfidReadEventRepository.findByReaderIdAndSourceEventId(fixture.reader.getId(), "event-1"))
            .thenReturn(Optional.of(existing));

        RfidReadEventBatchResponse response = service.ingest("api-key", batch(read("event-1", "TAG-1")), "127.0.0.1");

        assertThat(response.getDuplicate()).isEqualTo(1);
        assertThat(response.getResults()).singleElement().satisfies(result -> {
            assertThat(result.getStatus()).isEqualTo("DUPLICATE");
            assertThat(result.getErrorMessage()).isEqualTo("sourceEventId already ingested");
        });
        verify(rfidReadEventRepository, never()).save(any(RfidReadEvent.class));
        verify(rfidEventPublisher, never()).publish(any(RfidReadEvent.class));
    }

    @Test
    void ingestRejectsUnknownApiKey() {
        when(rfidApiKeyService.hashKey("api-key")).thenReturn("hashed");
        when(rfidReaderRepository.findByApiKeyHash("hashed")).thenReturn(Optional.empty());

        assertThrows(UnauthorizedException.class, () -> service.ingest("api-key", batch(read("event-1", "TAG-1")), "127.0.0.1"));
    }

    private void stubReader(RfidReader reader) {
        when(rfidApiKeyService.hashKey("api-key")).thenReturn("hashed");
        when(rfidReaderRepository.findByApiKeyHash("hashed")).thenReturn(Optional.of(reader));
        when(rfidReadEventRepository.save(any(RfidReadEvent.class))).thenAnswer(invocation -> {
            RfidReadEvent event = invocation.getArgument(0);
            if (event.getId() == null) {
                event.setId(UUID.randomUUID());
            }
            return event;
        });
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

    private record Fixture(Race race, Checkpoint checkpoint, RfidReader reader) {
    }
}
