package com.racetracker.security;

import com.racetracker.config.RaceTrackerProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class PublicRequestLimiter {

    public static final String CLIENT_HEADER = "X-Race-Tracker-Client";

    private final RequestRateLimiter requestRateLimiter;
    private final ClientAddressResolver clientAddressResolver;
    private final int perClientPerMinute;
    private final int perAddressPerMinute;

    public PublicRequestLimiter(
        RequestRateLimiter requestRateLimiter,
        ClientAddressResolver clientAddressResolver,
        RaceTrackerProperties properties
    ) {
        this.requestRateLimiter = requestRateLimiter;
        this.clientAddressResolver = clientAddressResolver;
        RaceTrackerProperties.Tracking tracking = properties.getTracking();
        this.perClientPerMinute = tracking.getPublicClientRequestsPerMinute();
        this.perAddressPerMinute = tracking.getPublicAddressRequestsPerMinute();
        if (perClientPerMinute < 1 || perAddressPerMinute < perClientPerMinute) {
            throw new IllegalStateException(
                "racetracker.tracking public request limits must be positive, and the per-address limit must be at least the per-client limit");
        }
    }

    public void check(UUID raceId, HttpServletRequest request) {
        requestRateLimiter.checkPublicTracking(
            raceId.toString(),
            clientAddressResolver.resolve(request),
            resolveClientKey(request),
            perClientPerMinute,
            perAddressPerMinute
        );
    }

    String resolveClientKey(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
            && authentication.isAuthenticated()
            && !(authentication instanceof AnonymousAuthenticationToken)
            && authentication.getName() != null
            && !authentication.getName().isBlank()) {
            return "user:" + authentication.getName();
        }
        String installId = request.getHeader(CLIENT_HEADER);
        if (installId == null || installId.isBlank()) {
            return null;
        }
        try {
            return "install:" + UUID.fromString(installId.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
