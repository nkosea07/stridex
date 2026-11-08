package com.racetracker.service;

import com.racetracker.config.RaceTrackerProperties;
import com.racetracker.repository.RfidReadEventRepository;
import com.racetracker.service.impl.RfidIngestMetrics;
import com.racetracker.service.impl.RfidStreamWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StreamOperations;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RfidStreamWorkerTest {

    @Mock private RedisTemplate<String, Object> redisTemplate;
    @Mock private StreamOperations<String, Object, Object> streamOperations;
    @Mock private RfidReadEventProcessor processor;
    @Mock private RfidReadEventRepository repository;
    @Mock private RfidEventPublisher publisher;
    @Mock private RfidIngestMetrics metrics;

    private RfidStreamWorker worker;
    private final UUID eventId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        worker = new RfidStreamWorker(redisTemplate, new RaceTrackerProperties(), processor, repository, publisher, metrics);
    }

    @SuppressWarnings("unchecked")
    private void streamReturnsOneRecord() {
        MapRecord<String, Object, Object> record = MapRecord.<String, Object, Object>create(
            "race.rfid.ingest", Map.of("eventId", eventId.toString())).withId(RecordId.of("1-0"));
        when(redisTemplate.opsForStream()).thenReturn((StreamOperations) streamOperations);
        when(streamOperations.read(any(Consumer.class), any(StreamReadOptions.class), any(StreamOffset.class)))
            .thenReturn(List.of(record));
    }

    @Test
    void failedProcessingIsRecordedAndAcknowledgedSoRetryIsDrivenByBackoff() {
        streamReturnsOneRecord();
        doThrow(new IllegalStateException("boom")).when(processor).process(eventId);
        when(processor.recordProcessingFailure(eq(eventId), any())).thenReturn(false);

        worker.processStream();

        verify(processor).recordProcessingFailure(eq(eventId), any(IllegalStateException.class));
        verify(streamOperations).acknowledge(anyString(), anyString(), any(RecordId.class));
    }

    @Test
    void unrecordedFailureLeavesTheStreamEntryPending() {
        streamReturnsOneRecord();
        doThrow(new IllegalStateException("boom")).when(processor).process(eventId);
        when(processor.recordProcessingFailure(eq(eventId), any())).thenThrow(new IllegalStateException("db down"));

        worker.processStream();

        verify(streamOperations, never()).acknowledge(anyString(), anyString(), any(RecordId.class));
    }

    @Test
    void recoveryRepublishesOnlyEventsThatAreDueAndSettled() {
        UUID due = UUID.randomUUID();
        when(repository.findRecoverableReceivedIds(any(OffsetDateTime.class), any(OffsetDateTime.class), any(Pageable.class)))
            .thenReturn(List.of(due));

        worker.republishUnprocessedEvents();

        verify(publisher).publish(due);
    }
}
