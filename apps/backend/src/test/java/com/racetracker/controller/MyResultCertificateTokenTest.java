package com.racetracker.controller;

import com.racetracker.dto.result.RaceResultDto;
import com.racetracker.dto.result.RaceResultPageDto;
import com.racetracker.security.CertificateTokenService;
import com.racetracker.service.ResultService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MyResultCertificateTokenTest {

    private final ResultService resultService = mock(ResultService.class);
    private final CertificateTokenService tokens = new CertificateTokenService("test-secret-that-is-long-enough-for-hmac-signing");
    private final ResultController controller = new ResultController(resultService, null, null, null, null, null, tokens,
        null, null, null, null, null);

    private RaceResultDto result(boolean official) {
        RaceResultDto dto = new RaceResultDto();
        dto.setRaceId(UUID.randomUUID());
        dto.setParticipantId(UUID.randomUUID());
        dto.setCertificateAvailable(official);
        return dto;
    }

    @Test
    void ownOfficialResultsCarryAVerifiableCertificateToken() {
        RaceResultDto official = result(true);
        RaceResultDto provisional = result(false);
        when(resultService.getMyResultsPage(0, 50))
            .thenReturn(new RaceResultPageDto(List.of(official, provisional), 0, 50, 2, 1, 0));

        controller.myResultsPage(0, 50);

        assertThat(provisional.getCertificateToken()).isNull();
        assertThat(tokens.verify(official.getCertificateToken())).hasValueSatisfying(claims -> {
            assertThat(claims.raceId()).isEqualTo(official.getRaceId());
            assertThat(claims.participantId()).isEqualTo(official.getParticipantId());
        });
    }
}
