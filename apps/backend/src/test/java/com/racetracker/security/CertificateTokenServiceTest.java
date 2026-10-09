package com.racetracker.security;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CertificateTokenServiceTest {

    private final CertificateTokenService service =
        new CertificateTokenService("test-certificate-token-secret-value-1234567890");

    @Test
    void issuedTokenVerifiesForSameRaceAndParticipant() {
        UUID raceId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();

        String token = service.issue(raceId, participantId);
        Optional<CertificateTokenService.Claims> claims = service.verify(token);

        assertThat(claims).isPresent();
        assertThat(claims.get().raceId()).isEqualTo(raceId);
        assertThat(claims.get().participantId()).isEqualTo(participantId);
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = service.issue(UUID.randomUUID(), UUID.randomUUID());
        String tampered = token.substring(0, token.length() - 2) + "xx";

        assertThat(service.verify(tampered)).isEmpty();
    }

    @Test
    void malformedTokenIsRejected() {
        assertThat(service.verify(null)).isEmpty();
        assertThat(service.verify("not-a-token")).isEmpty();
        assertThat(service.verify("onlyonehalf.")).isEmpty();
    }
}
