package com.racetracker.service.impl;

import com.racetracker.config.RaceTrackerProperties;
import com.racetracker.model.RfidReadEvent;
import com.racetracker.repository.RfidReadEventRepository;
import com.racetracker.service.RfidEventPublisher;
import com.racetracker.service.RfidReadEventProcessor;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class RfidStreamWorker {

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
        for (RfidReadEvent event : rfidReadEventRepository.findTop100ByStatusOrderByReceivedAtAsc(RfidReadEvent.RfidReadEventStatus.RECEIVED)) {
            rfidEventPublisher.publish(event);
        }
    }

    private void processRecord(MapRecord<String, Object, Object> record) {
        try {
            Object rawEventId = record.getValue().get("eventId");
            if (rawEventId != null) {
                rfidReadEventProcessor.process(UUID.fromString(rawEventId.toString()));
            }
            redisTemplate.opsForStream().acknowledge(
                raceTrackerProperties.getRfid().getStreamKey(),
                raceTrackerProperties.getRfid().getConsumerGroup(),
                record.getId()
            );
        } catch (Exception ex) {
            rfidIngestMetrics.incrementError();
            log.error("Failed to process RFID stream record {}: {}", record.getId(), ex.getMessage());
        }
    }
}
