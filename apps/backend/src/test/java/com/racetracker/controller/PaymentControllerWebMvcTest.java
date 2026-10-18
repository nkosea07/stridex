package com.racetracker.controller;

import com.racetracker.dto.participant.PaymentDto;
import com.racetracker.security.JwtAuthenticationFilter;
import com.racetracker.security.RaceAccessGuard;
import com.racetracker.service.AuditEventService;
import com.racetracker.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PaymentController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {
    "server.servlet.context-path=",
    "racetracker.api.version=1"
})
class PaymentControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PaymentService paymentService;

    @MockBean
    private AuditEventService auditEventService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private RaceAccessGuard raceAccessGuard;

    @Test
    @WithMockUser(username = "admin@example.com", roles = "RACE_ADMIN")
    void raceAdminCannotStartCheckoutForDeniedRace() throws Exception {
        UUID raceId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        doThrow(new AccessDeniedException("denied"))
            .when(raceAccessGuard).assertCanManage(raceId);

        mockMvc.perform(post("/v1/races/{raceId}/participants/{participantId}/payment/checkout", raceId, participantId))
            .andExpect(status().isForbidden());

        verifyNoInteractions(paymentService, auditEventService);
    }

    @Test
    void athleteCanStillStartCheckout() throws Exception {
        UUID raceId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        PaymentDto payment = new PaymentDto();
        payment.setProvider("PESEPAY");
        when(paymentService.startCheckout(raceId, participantId, null, "athlete@example.com", true)).thenReturn(payment);

        var response = new PaymentController(paymentService, auditEventService, raceAccessGuard)
            .startCheckout(raceId, participantId, null, new UsernamePasswordAuthenticationToken(
                "athlete@example.com", null, java.util.List.of(new SimpleGrantedAuthority("ROLE_ATHLETE"))));

        org.assertj.core.api.Assertions.assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(raceAccessGuard, org.mockito.Mockito.never()).assertCanManage(raceId);
        verify(paymentService).startCheckout(raceId, participantId, null, "athlete@example.com", true);
    }
}
