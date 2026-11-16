package com.racetracker.service.impl;

import com.racetracker.config.RaceTrackerProperties;
import com.racetracker.repository.RfidReadEventRepository;
import com.racetracker.service.RfidEventPublisher;
import com.racetracker.service.RfidReadEventProcessor;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class RfidStreamWorker {

    private static final Duration RECOVERY_SETTLE_DELAY = Duration.ofSeconds(2);
    private static final int RECOVERY_BATCH_SIZE = 100;

    private final RedisTemplate<String, Object> redisTemplate;
    private final RaceTrackerProperties raceTrackerProperties;
    private final RfidReadEventProcessor rfidReadEventProcessor;
    private final RfidReadEventRepository rfidReadEventRepository;
    private final RfidEventPublisher rfidEventPublisher;
    private final RfidIngestMetrics rfidIngestMetrics;

    public RfidStreamWorker(
        RedisTemplate<String, Object> redisTemplate,
        RaceTrackerProperties raceTrackerProperties,
        RfidReadEventProcessor rfidReadEventProcessor,
        RfidReadEventRepository rfidReadEventRepository,
        RfidEventPublisher rfidEventPublisher,
        RfidIngestMetrics rfidIngestMetrics
    ) {
        this.redisTemplate = redisTemplate;
        this.raceTrackerProperties = raceTrackerProperties;
        this.rfidReadEventProcessor = rfidReadEventProcessor;
        this.rfidReadEventRepository = rfidReadEventRepository;
        this.rfidEventPublisher = rfidEventPublisher;
        this.rfidIngestMetrics = rfidIngestMetrics;
    }

    @PostConstruct
    void ensureConsumerGroup() {
        try {
            redisTemplate.opsForStream().add(MapRecord.create(
                raceTrackerProperties.getRfid().getStreamKey(),
                java.util.Map.of("bootstrap", "true")
            ));
            redisTemplate.opsForStream().createGroup(
                raceTrackerProperties.getRfid().getStreamKey(),
                ReadOffset.latest(),
                raceTrackerProperties.getRfid().getConsumerGroup()
            );
        } catch (Exception ex) {
            log.debug("RFID stream consumer group init skipped: {}", ex.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${racetracker.rfid.worker-fixed-delay-millis:500}")
    public void processStream() {
        try {
            List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().read(
                Consumer.from(
                    raceTrackerProperties.getRfid().getConsumerGroup(),
                    raceTrackerProperties.getRfid().getConsumerName()
                ),
                StreamReadOptions.empty()
                    .count(raceTrackerProperties.getRfid().getWorkerBatchSize())
                    .block(Duration.ofMillis(raceTrackerProperties.getRfid().getWorkerPollTimeoutMillis())),
                StreamOffset.create(
                    raceTrackerProperties.getRfid().getStreamKey(),
                    ReadOffset.lastConsumed()
                )
            );
            if (records == null || records.isEmpty()) {
                return;
            }
            for (MapRecord<String, Object, Object> record : records) {
                processRecord(record);
            }
        } catch (Exception ex) {
            log.debug("RFID stream worker poll skipped: {}", ex.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${racetracker.rfid.recovery-fixed-delay-millis:5000}")
    public void republishUnprocessedEvents() {
        OffsetDateTime now = OffsetDateTime.now();
        for (UUID eventId : rfidReadEventRepository.findRecoverableReceivedIds(
            now.minus(RECOVERY_SETTLE_DELAY), now, PageRequest.of(0, RECOVERY_BATCH_SIZE))) {
            rfidEventPublisher.publish(eventId);
        }
    }

    private void processRecord(MapRecord<String, Object, Object> record) {
        UUID eventId = null;
        try {
            Object rawEventId = record.getValue().get("eventId");
            if (rawEventId != null) {
                eventId = UUID.fromString(rawEventId.toString());
                rfidReadEventProcessor.process(eventId);
            }
        } catch (Exception ex) {
            rfidIngestMetrics.incrementError();
            log.error("Failed to process RFID stream record {}: {}", record.getId(), ex.getMessage());
            if (eventId != null && !recordFailure(eventId, ex)) {
                return;
            }
        }
        acknowledge(record);
    }

    private boolean recordFailure(UUID eventId, Exception failure) {
        try {
            if (rfidReadEventProcessor.recordProcessingFailure(eventId, failure)) {
                log.error("RFID event {} exhausted processing attempts and was marked ERROR", eventId);
            }
            return true;
        } catch (Exception ex) {
            log.error("Failed to record RFID processing failure for event {}: {}", eventId, ex.getMessage());
            return false;
        }
    }

    private void acknowledge(MapRecord<String, Object, Object> record) {
        try {
            redisTemplate.opsForStream().acknowledge(
                raceTrackerProperties.getRfid().getStreamKey(),
                raceTrackerProperties.getRfid().getConsumerGroup(),
                record.getId()
            );
        } catch (Exception ex) {
            log.debug("RFID stream acknowledge skipped for {}: {}", record.getId(), ex.getMessage());
        }
    }
}
