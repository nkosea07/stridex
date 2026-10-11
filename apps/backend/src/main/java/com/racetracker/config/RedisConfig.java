package com.racetracker.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Configuration
public class RedisConfig {

    public static final String TRACKING_TOPIC = "race.tracking.updates";
    public static final String ALERTS_TOPIC = "race.spectator.alerts";

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new Jackson2JsonRedisSerializer<>(Object.class));
        return template;
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10))
            .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new Jackson2JsonRedisSerializer<>(Object.class)));

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(config)
            .withInitialCacheConfigurations(Map.of("lastCheckpoint", config.entryTtl(Duration.ofSeconds(5))))
            .build();
    }

    @Bean
    public RedisMessageListenerContainer container(RedisConnectionFactory connectionFactory,
                                           MessageListenerAdapter listenerAdapter,
                                           MessageListenerAdapter alertsListenerAdapter) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(listenerAdapter, new PatternTopic(TRACKING_TOPIC));
        container.addMessageListener(alertsListenerAdapter, new PatternTopic(ALERTS_TOPIC));
        return container;
    }

    @Bean
    public MessageListenerAdapter listenerAdapter(RedisMessageListener receiver) {
        return new MessageListenerAdapter(receiver, "receiveMessage");
    }

    @Bean
    public MessageListenerAdapter alertsListenerAdapter(RedisMessageListener receiver) {
        return new MessageListenerAdapter(receiver, "receiveAlert");
    }

    @Configuration
    public static class RedisMessageListener {
        private final SimpMessagingTemplate messagingTemplate;
        private final ObjectMapper objectMapper;
        private final MeterRegistry meterRegistry;

        public RedisMessageListener(SimpMessagingTemplate messagingTemplate, ObjectMapper objectMapper, MeterRegistry meterRegistry) {
            this.messagingTemplate = messagingTemplate;
            this.objectMapper = objectMapper;
            this.meterRegistry = meterRegistry;
        }

        public void receiveMessage(String message) {
            try {
                Map<String, Object> payload = objectMapper.readValue(message, Map.class);
                String raceId = (String) payload.get("raceId");
                Object data = payload.get("data");
                Object publishedAtEpochMs = payload.get("publishedAtEpochMs");
                
                log.debug("Redis message received for race {}: {}", raceId, message);
                messagingTemplate.convertAndSend("/topic/race." + raceId, data);
                if (publishedAtEpochMs instanceof Number publishedAt) {
                    meterRegistry.timer("tracking.public.live_fanout.latency")
                        .record(Math.max(0L, System.currentTimeMillis() - publishedAt.longValue()), java.util.concurrent.TimeUnit.MILLISECONDS);
                }
            } catch (Exception e) {
                log.error("Failed to process Redis message: {}", e.getMessage());
            }
        }

        public void receiveAlert(String message) {
            try {
                Map<String, Object> payload = objectMapper.readValue(message, Map.class);
                String targetUserId = (String) payload.get("targetUserId");
                if (targetUserId == null) {
                    return;
                }
                messagingTemplate.convertAndSend("/topic/user." + targetUserId, payload.get("data"));
            } catch (Exception e) {
                log.error("Failed to process spectator alert: {}", e.getMessage());
            }
        }
    }
}
