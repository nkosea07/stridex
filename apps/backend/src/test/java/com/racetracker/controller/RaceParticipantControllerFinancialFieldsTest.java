package com.racetracker.controller;

import com.racetracker.dto.participant.ParticipantDto;
import com.racetracker.security.RaceAccessGuard;
import com.racetracker.service.AuditEventService;
import com.racetracker.service.ParticipantService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RaceParticipantControllerFinancialFieldsTest {

    private final ParticipantService participantService = mock(ParticipantService.class);
    private final AuditEventService auditEventService = mock(AuditEventService.class);
    private final RaceAccessGuard raceAccessGuard = mock(RaceAccessGuard.class);
    private RaceParticipantController controller;
    private final UUID raceId = UUID.randomUUID();
    private final UUID participantId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        controller = new RaceParticipantController(participantService, auditEventService, raceAccessGuard);
    }

    @Test
    void operationalStaffDoNotReceiveFinancialReferences() {
        ParticipantDto participant = participant();
        when(participantService.listByRace(raceId, null)).thenReturn(List.of(participant));

        List<ParticipantDto> result = controller.list(raceId, null, authentication("ROLE_VOLUNTEER")).getBody();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPaymentReference()).isNull();
        assertThat(result.get(0).getPaymentAmount()).isNull();
        assertThat(result.get(0).getPaymentVerifiedAt()).isNull();
        assertThat(result.get(0).getPaymentStatus()).isEqualTo("PAID");
    }

    @Test
    void raceAdministratorsRetainFinancialReferences() {
        ParticipantDto participant = participant();
        when(participantService.getByRaceAndId(raceId, participantId)).thenReturn(participant);

        ParticipantDto result = controller.get(raceId, participantId, authentication("ROLE_RACE_ADMIN")).getBody();

        assertThat(result.getPaymentReference()).isEqualTo("PAY-1");
        assertThat(result.getPaymentAmount()).isEqualByComparingTo(BigDecimal.valueOf(50));
    }

    private ParticipantDto participant() {
        ParticipantDto participant = new ParticipantDto();
        participant.setId(participantId);
        participant.setPaymentStatus("PAID");
        participant.setPaymentReference("PAY-1");
        participant.setPaymentAmount(BigDecimal.valueOf(50));
        participant.setPaymentVerifiedAt(OffsetDateTime.now());
        return participant;
    }

    private Authentication authentication(String role) {
        return new UsernamePasswordAuthenticationToken("staff@example.com", null,
            List.of(new SimpleGrantedAuthority(role)));
    }
}
