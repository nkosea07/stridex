package com.racetracker.service.impl;

import com.racetracker.config.RaceTrackerProperties;
import com.racetracker.model.RfidReadEvent;
import com.racetracker.service.RfidEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class RedisStreamRfidEventPublisher implements RfidEventPublisher {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RaceTrackerProperties raceTrackerProperties;

    public RedisStreamRfidEventPublisher(
        RedisTemplate<String, Object> redisTemplate,
        RaceTrackerProperties raceTrackerProperties
    ) {
        this.redisTemplate = redisTemplate;
        this.raceTrackerProperties = raceTrackerProperties;
    }

    @Override
    public void publish(UUID eventId) {
        if (eventId == null) {
            return;
        }
        try {
            redisTemplate.opsForStream().add(MapRecord.create(
                raceTrackerProperties.getRfid().getStreamKey(),
                Map.of("eventId", eventId.toString())
            ));
        } catch (RedisConnectionFailureException ex) {
            log.warn("RFID stream publish deferred for event {} because Redis is unavailable", eventId);
        } catch (Exception ex) {
            log.error("Failed to publish RFID event {} to Redis stream: {}", eventId, ex.getMessage());
        }
    }

    @Override
    public void publish(RfidReadEvent event) {
        publish(event != null ? event.getId() : null);
    }
}
