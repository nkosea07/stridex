package com.racetracker.security;

import com.racetracker.config.RaceTrackerProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublicRequestLimiterTest {

    private final RequestRateLimiter rateLimiter = mock(RequestRateLimiter.class);
    private final ClientAddressResolver addressResolver = mock(ClientAddressResolver.class);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private PublicRequestLimiter limiter(int perClient, int perAddress) {
        RaceTrackerProperties properties = new RaceTrackerProperties();
        RaceTrackerProperties.Tracking tracking = new RaceTrackerProperties.Tracking();
        tracking.setPublicClientRequestsPerMinute(perClient);
        tracking.setPublicAddressRequestsPerMinute(perAddress);
        properties.setTracking(tracking);
        return new PublicRequestLimiter(rateLimiter, addressResolver, properties);
    }

    @Test
    void signedInSpectatorsAreLimitedPerAccountRatherThanPerCarrierAddress() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            "fan@example.com", null, List.of(new SimpleGrantedAuthority("ROLE_PARTICIPANT"))));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(PublicRequestLimiter.CLIENT_HEADER, UUID.randomUUID().toString());
        when(addressResolver.resolve(request)).thenReturn("100.64.0.1");
        UUID raceId = UUID.randomUUID();

        limiter(120, 1200).check(raceId, request);

        verify(rateLimiter).checkPublicTracking(raceId.toString(), "100.64.0.1", "user:fan@example.com", 120, 1200);
    }

    @Test
    void anonymousAppInstallsAreLimitedPerInstallId() {
        UUID installId = UUID.randomUUID();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(PublicRequestLimiter.CLIENT_HEADER, " " + installId + " ");

        assertThat(limiter(120, 1200).resolveClientKey(request)).isEqualTo("install:" + installId);
    }

    @Test
    void anonymousOrMalformedClientsFallBackToThePerAddressBucket() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
            "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        MockHttpServletRequest missing = new MockHttpServletRequest();
        MockHttpServletRequest malformed = new MockHttpServletRequest();
        malformed.addHeader(PublicRequestLimiter.CLIENT_HEADER, "not-a-uuid;drop table");

        PublicRequestLimiter limiter = limiter(120, 1200);

        assertThat(limiter.resolveClientKey(missing)).isNull();
        assertThat(limiter.resolveClientKey(malformed)).isNull();
    }

    @Test
    void rejectsLimitsThatWouldLetOneClientExceedItsAddressCeiling() {
        assertThatThrownBy(() -> limiter(0, 1200)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> limiter(500, 100)).isInstanceOf(IllegalStateException.class);
    }
}
