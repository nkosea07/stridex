package com.racetracker.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.racetracker.config.RedisConfig;
import com.racetracker.dto.notification.NotificationDto;
import com.racetracker.dto.tracking.LiveTrackingResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class TrackingLivePublisherImpl implements TrackingLivePublisher {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    @Async
    @Override
    public void publishLocationUpdate(UUID raceId, LiveTrackingResponse payload) {
        try {
            Map<String, Object> message = Map.of(
                "raceId", raceId.toString(),
                "data", payload,
                "publishedAtEpochMs", System.currentTimeMillis()
            );
            
            String jsonMessage = objectMapper.writeValueAsString(message);
            redisTemplate.convertAndSend(RedisConfig.TRACKING_TOPIC, jsonMessage);
            
            log.debug("Published live update to Redis for race {}", raceId);
        } catch (Exception e) {
            log.error("Failed to publish live update to Redis: {}", e.getMessage());
        }
    }

    @Async
    @Override
    public void publishUserAlert(UUID userId, NotificationDto payload) {
        try {
            Map<String, Object> message = Map.of(
                "targetUserId", userId.toString(),
                "data", payload,
                "publishedAtEpochMs", System.currentTimeMillis()
            );
            redisTemplate.convertAndSend(RedisConfig.ALERTS_TOPIC, objectMapper.writeValueAsString(message));
            log.debug("Published spectator alert to Redis for user {}", userId);
        } catch (Exception e) {
            log.error("Failed to publish spectator alert to Redis: {}", e.getMessage());
        }
    }
}
