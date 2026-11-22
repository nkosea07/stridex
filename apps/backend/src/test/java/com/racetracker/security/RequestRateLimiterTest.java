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
    void anonymousPublicTrackingWithoutClientIdKeepsOneHashedPerAddressBucket() {
        doReturn(0L).when(redisTemplate).execute(any(RedisScript.class), anyList(), any(Object[].class));

        limiter.checkPublicTracking("race-1", "198.51.100.7", null, 120, 1200);

        org.mockito.ArgumentCaptor<List<String>> keys = org.mockito.ArgumentCaptor.forClass(List.class);
        org.mockito.ArgumentCaptor<Object[]> arguments = org.mockito.ArgumentCaptor.forClass(Object[].class);
        org.mockito.Mockito.verify(redisTemplate).execute(any(RedisScript.class), keys.capture(), arguments.capture());
        assertEquals(1, keys.getValue().size());
        assertTrue(keys.getValue().get(0).startsWith("rt:limit:public-tracking:"));
        assertEquals(false, keys.getValue().get(0).contains("198.51.100.7"));
        assertEquals("120", String.valueOf(arguments.getValue()[0]));
    }

    @Test
    void identifiedPublicTrackingClientsShareOnlyAHigherPerAddressCeiling() {
        doReturn(0L).when(redisTemplate).execute(any(RedisScript.class), anyList(), any(Object[].class));

        limiter.checkPublicTracking("race-1", "198.51.100.7", "install:6f1c2b9e-7a4d-4c1e-9a52-3b8f0d6e2a11", 120, 1200);

        org.mockito.ArgumentCaptor<List<String>> keys = org.mockito.ArgumentCaptor.forClass(List.class);
        org.mockito.ArgumentCaptor<Object[]> arguments = org.mockito.ArgumentCaptor.forClass(Object[].class);
        org.mockito.Mockito.verify(redisTemplate).execute(any(RedisScript.class), keys.capture(), arguments.capture());
        assertEquals(2, keys.getValue().size());
        assertTrue(keys.getValue().get(0).startsWith("rt:limit:public-tracking:client:"));
        assertTrue(keys.getValue().get(1).startsWith("rt:limit:public-tracking:address:"));
        assertEquals(false, keys.getValue().stream().anyMatch(key -> key.contains("198.51.100.7") || key.contains("6f1c2b9e")));
        List<String> values = List.of(arguments.getValue()).stream().map(String::valueOf).toList();
        assertEquals("120", values.get(0));
        assertEquals("1200", values.get(2));
    }

    @Test
    void verificationResendIsLimitedPerAccountAndIpWithoutStoringTheEmail() {
        doReturn(0L).when(redisTemplate).execute(any(RedisScript.class), anyList(), any(Object[].class));

        limiter.checkVerificationResend("Runner@Example.com", "203.0.113.20");

        org.mockito.ArgumentCaptor<List<String>> keys = org.mockito.ArgumentCaptor.forClass(List.class);
        org.mockito.ArgumentCaptor<Object[]> arguments = org.mockito.ArgumentCaptor.forClass(Object[].class);
        org.mockito.Mockito.verify(redisTemplate).execute(any(RedisScript.class), keys.capture(), arguments.capture());
        assertEquals(2, keys.getValue().size());
        assertTrue(keys.getValue().get(0).startsWith("rt:limit:verification-resend:ip:"));
        assertTrue(keys.getValue().get(1).startsWith("rt:limit:verification-resend:account:"));
        assertTrue(keys.getValue().stream().noneMatch(key -> key.contains("runner@example.com") || key.contains("203.0.113.20")));
        List<String> values = List.of(arguments.getValue()).stream().map(String::valueOf).toList();
        assertEquals("10", values.get(0));
        assertEquals("3", values.get(2));
    }

    @Test
    void oauthUsesOneHashedIpBucket() {
        doReturn(0L).when(redisTemplate).execute(any(RedisScript.class), anyList(), any(Object[].class));

        limiter.checkOAuth("203.0.113.20");

        org.mockito.ArgumentCaptor<List<String>> keys = org.mockito.ArgumentCaptor.forClass(List.class);
        org.mockito.ArgumentCaptor<Object[]> arguments = org.mockito.ArgumentCaptor.forClass(Object[].class);
        org.mockito.Mockito.verify(redisTemplate).execute(any(RedisScript.class), keys.capture(), arguments.capture());
        assertEquals(1, keys.getValue().size());
        assertTrue(keys.getValue().get(0).startsWith("rt:limit:oauth:ip:"));
        assertTrue(keys.getValue().stream().noneMatch(key -> key.contains("203.0.113.20")));
        List<String> values = List.of(arguments.getValue()).stream().map(String::valueOf).toList();
        assertEquals("10", values.get(0));
        assertTrue(Integer.parseInt(values.get(1)) > 0 && Integer.parseInt(values.get(1)) <= 900);
    }

    @Test
    void rfidUsesIpAndHashedReaderBuckets() {
        doReturn(0L).when(redisTemplate).execute(any(RedisScript.class), anyList(), any(Object[].class));

        limiter.checkRfid("reader-secret", "203.0.113.20");

        org.mockito.ArgumentCaptor<List<String>> keys = org.mockito.ArgumentCaptor.forClass(List.class);
        org.mockito.ArgumentCaptor<Object[]> arguments = org.mockito.ArgumentCaptor.forClass(Object[].class);
        org.mockito.Mockito.verify(redisTemplate).execute(any(RedisScript.class), keys.capture(), arguments.capture());
        assertEquals(2, keys.getValue().size());
        assertTrue(keys.getValue().get(0).startsWith("rt:limit:rfid:ip:"));
        assertTrue(keys.getValue().get(1).startsWith("rt:limit:rfid:reader:"));
        assertTrue(keys.getValue().stream().noneMatch(key -> key.contains("reader-secret") || key.contains("203.0.113.20")));
        List<String> values = List.of(arguments.getValue()).stream().map(String::valueOf).toList();
        assertEquals("1200", values.get(0));
        assertEquals("600", values.get(2));
    }

    @Test
    void rfidWithoutReaderKeyUsesOnlyIpBucket() {
        doReturn(0L).when(redisTemplate).execute(any(RedisScript.class), anyList(), any(Object[].class));

        limiter.checkRfid(null, "203.0.113.20");

        org.mockito.ArgumentCaptor<List<String>> keys = org.mockito.ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(redisTemplate).execute(any(RedisScript.class), keys.capture(), any(Object[].class));
        assertEquals(1, keys.getValue().size());
        assertTrue(keys.getValue().get(0).startsWith("rt:limit:rfid:ip:"));
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
