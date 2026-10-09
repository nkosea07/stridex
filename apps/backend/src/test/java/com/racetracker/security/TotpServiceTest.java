package com.racetracker.security;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TotpServiceTest {

    private final TotpService service = new TotpService();
    private static final String RFC_SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    @Test
    void codeMatchesRfc6238Sha1Vector() {
        assertThat(service.codeAt(RFC_SECRET, 59)).isEqualTo("287082");
    }

    @Test
    void verificationAllowsOnlyTheAdjacentThirtySecondPeriods() {
        String current = service.codeAt(RFC_SECRET, 60);
        String previous = service.codeAt(RFC_SECRET, 30);

        assertThat(service.verify(RFC_SECRET, current, Instant.ofEpochSecond(60))).isTrue();
        assertThat(service.verify(RFC_SECRET, previous, Instant.ofEpochSecond(60))).isTrue();
        assertThat(service.verify(RFC_SECRET, previous, Instant.ofEpochSecond(120))).isFalse();
        assertThat(service.verify(RFC_SECRET, "12AB56", Instant.ofEpochSecond(60))).isFalse();
    }

    @Test
    void createsAuthenticatorProvisioningUriWithRequiredPeriodAndDigits() {
        String uri = service.provisioningUri("runner@example.com", "ABC123");

        assertThat(uri).contains("otpauth://totp/", "secret=ABC123", "digits=6", "period=30", "algorithm=SHA1");
    }
}
