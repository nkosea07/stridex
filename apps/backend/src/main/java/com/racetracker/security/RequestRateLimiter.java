package com.racetracker.security;

import com.racetracker.exception.ServiceUnavailableException;
import com.racetracker.exception.TooManyRequestsException;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Component
public class RequestRateLimiter {

    private static final DefaultRedisScript<Long> ATOMIC_LIMIT_SCRIPT = new DefaultRedisScript<>("""
        local retry = 0
        for i = 1, #KEYS do
          local current = tonumber(redis.call('GET', KEYS[i]) or '0')
          local limit = tonumber(ARGV[(i - 1) * 2 + 1])
          if current >= limit then
            local ttl = tonumber(redis.call('TTL', KEYS[i]))
            if ttl < 1 then ttl = tonumber(ARGV[(i - 1) * 2 + 2]) end
            if ttl > retry then retry = ttl end
          end
        end
        if retry > 0 then return retry end
        for i = 1, #KEYS do
          local count = redis.call('INCR', KEYS[i])
          if count == 1 then redis.call('EXPIRE', KEYS[i], ARGV[(i - 1) * 2 + 2]) end
        end
        return 0
        """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final MeterRegistry meterRegistry;

    public RequestRateLimiter(StringRedisTemplate redisTemplate, MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.meterRegistry = meterRegistry;
    }

    public void checkLogin(String email, String ipAddress) {
        check("login", List.of(
            bucket("login:account", hash(normalizeAccount(email)), 5, 900),
            bucket("login:ip", hash(ipAddress), 20, 900)
        ));
    }

    public void checkRegistration(String ipAddress) {
        check("registration", List.of(bucket("registration:ip", hash(ipAddress), 10, 3600)));
    }

    public void checkPasswordReset(String email, String ipAddress) {
        List<Bucket> buckets = new ArrayList<>();
        buckets.add(bucket("password-reset:ip", hash(ipAddress), 5, 900));
        if (email != null && !email.isBlank()) {
            buckets.add(bucket("password-reset:account", hash(normalizeAccount(email)), 3, 3600));
        }
        check("password_reset", buckets);
    }

    public void checkMfa(String accountIdentifier, String ipAddress) {
        check("mfa", List.of(
            bucket("mfa:account", hash(accountIdentifier), 5, 600),
            bucket("mfa:ip", hash(ipAddress), 5, 600)
        ));
    }

    public void checkPublicTracking(String raceId, String ipAddress) {
        check("public_tracking", List.of(
            bucket("public-tracking", hash(raceId + ":" + ipAddress), 120, 60)
        ));
    }

    private void check(String policy, List<Bucket> buckets) {
        long now = Instant.now().getEpochSecond();
        List<String> keys = new ArrayList<>(buckets.size());
        List<String> args = new ArrayList<>(buckets.size() * 2);
        for (Bucket bucket : buckets) {
            long window = Math.floorDiv(now, bucket.windowSeconds());
            long expiresIn = bucket.windowSeconds() - Math.floorMod(now, bucket.windowSeconds());
            keys.add("rt:limit:" + bucket.key() + ":" + window);
            args.add(String.valueOf(bucket.limit()));
            args.add(String.valueOf(expiresIn));
        }

        Long retryAfter;
        try {
            retryAfter = redisTemplate.execute(ATOMIC_LIMIT_SCRIPT, keys, args.toArray(String[]::new));
        } catch (DataAccessException ex) {
            meterRegistry.counter("security.rate_limit.store_failure", "policy", policy).increment();
            throw new ServiceUnavailableException("Rate-limit service is unavailable; retry this request shortly");
        }
        if (retryAfter == null) {
            meterRegistry.counter("security.rate_limit.store_failure", "policy", policy).increment();
            throw new ServiceUnavailableException("Rate-limit service is unavailable; retry this request shortly");
        }
        if (retryAfter > 0) {
            meterRegistry.counter("security.rate_limit.blocked", "policy", policy).increment();
            throw new TooManyRequestsException("Too many requests. Please retry later", retryAfter);
        }
    }

    private Bucket bucket(String key, String identityHash, int limit, int windowSeconds) {
        return new Bucket(key + ":" + identityHash, limit, windowSeconds);
    }

    private String normalizeAccount(String email) {
        return email == null ? "unknown" : email.trim().toLowerCase(Locale.ROOT);
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(String.valueOf(value).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not hash rate-limit key", ex);
        }
    }

    private record Bucket(String key, int limit, int windowSeconds) { }
}
