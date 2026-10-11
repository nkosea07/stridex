package com.racetracker.security;

import com.racetracker.exception.ServiceUnavailableException;
import com.racetracker.exception.TooManyRequestsException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

class RequestRateLimiterTest {

    private StringRedisTemplate redisTemplate;
    private SimpleMeterRegistry meterRegistry;
    private RequestRateLimiter limiter;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        meterRegistry = new SimpleMeterRegistry();
        limiter = new RequestRateLimiter(redisTemplate, meterRegistry);
    }

    @AfterEach
    void tearDown() {
        meterRegistry.close();
    }

    @Test
    void loginUsesOneAtomicScriptAndHashedAccountAndIpKeys() {
        doReturn(0L).when(redisTemplate).execute(any(RedisScript.class), anyList(), any(Object[].class));

        limiter.checkLogin("Runner@Example.com", "203.0.113.20");

        org.mockito.ArgumentCaptor<List<String>> keys = org.mockito.ArgumentCaptor.forClass(List.class);
        org.mockito.ArgumentCaptor<Object[]> arguments = org.mockito.ArgumentCaptor.forClass(Object[].class);
        org.mockito.Mockito.verify(redisTemplate).execute(any(RedisScript.class), keys.capture(), arguments.capture());
        assertEquals(2, keys.getValue().size());
        assertEquals(false, keys.getValue().stream().anyMatch(key -> key.contains("runner@example.com") || key.contains("203.0.113.20")));
        List<String> values = List.of(arguments.getValue()).stream().map(String::valueOf).toList();
        assertEquals(List.of("5", values.get(1), "20", values.get(3)), values);
        assertTrue(Integer.parseInt(values.get(1)) > 0 && Integer.parseInt(values.get(1)) <= 900);
        assertEquals(values.get(1), values.get(3));
    }

    @Test
    void rejectedRequestIncludesRetryWindowAndIncrementsMetric() {
        doReturn(37L).when(redisTemplate).execute(any(RedisScript.class), anyList(), any(Object[].class));

        TooManyRequestsException exception = assertThrows(
            TooManyRequestsException.class,
            () -> limiter.checkRegistration("203.0.113.20")
        );

        assertEquals(37, exception.getRetryAfterSeconds());
        assertEquals(1.0, meterRegistry.get("security.rate_limit.blocked").tag("policy", "registration").counter().count());
    }

    @Test
    void redisFailureFailsClosedAndIsMeasured() {
        doThrow(new DataAccessResourceFailureException("offline"))
            .when(redisTemplate).execute(any(RedisScript.class), anyList(), any(Object[].class));

        assertThrows(ServiceUnavailableException.class, () -> limiter.checkMfa("runner@example.com", "203.0.113.20"));
        assertEquals(1.0, meterRegistry.get("security.rate_limit.store_failure").tag("policy", "mfa").counter().count());
    }
}
