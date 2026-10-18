package com.racetracker.controller;

import com.racetracker.dto.participant.PaymentDto;
import com.racetracker.dto.participant.PaymentRefundRequest;
import com.racetracker.security.RaceAccessGuard;
import com.racetracker.service.AuditEventService;
import com.racetracker.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
public class PaymentController {

    private final PaymentService paymentService;
    private final AuditEventService auditEventService;
    private final RaceAccessGuard raceAccessGuard;

    public PaymentController(PaymentService paymentService, AuditEventService auditEventService, RaceAccessGuard raceAccessGuard) {
        this.paymentService = paymentService;
        this.auditEventService = auditEventService;
        this.raceAccessGuard = raceAccessGuard;
    }

    @PostMapping("/v${racetracker.api.version}/races/{raceId}/participants/{participantId}/payment/checkout")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL', 'ATHLETE')")
    public ResponseEntity<PaymentDto> startCheckout(
        @PathVariable UUID raceId,
        @PathVariable UUID participantId,
        @RequestParam(required = false) String promoCode,
        Authentication authentication
    ) {
        boolean athleteSelfService = isAthleteSelfService(authentication);
        if (!athleteSelfService) {
            raceAccessGuard.assertCanManage(raceId);
        }
        PaymentDto payment = paymentService.startCheckout(
            raceId, participantId, promoCode, authentication.getName(), athleteSelfService);
        auditEventService.recordForActor("PAYMENT_CHECKOUT_STARTED", authentication.getName(), "PARTICIPANT",
            participantId.toString(), "SUCCESS", Map.of("provider", String.valueOf(payment.getProvider())));
        return ResponseEntity.ok(payment);
    }

    private boolean isAthleteSelfService(Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        boolean athlete = authentication.getAuthorities().stream()
            .anyMatch(authority -> "ROLE_ATHLETE".equals(authority.getAuthority()));
        boolean staff = authentication.getAuthorities().stream()
            .anyMatch(authority -> java.util.Set.of(
                "ROLE_SUPER_ADMIN", "ROLE_RACE_ADMIN", "ROLE_OFFICIAL", "ROLE_VOLUNTEER"
            ).contains(authority.getAuthority()));
        return athlete && !staff;
    }

    @PostMapping("/v${racetracker.api.version}/races/{raceId}/participants/{participantId}/payment/refund")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')")
    public ResponseEntity<PaymentDto> refund(
        @PathVariable UUID raceId,
        @PathVariable UUID participantId,
        @RequestBody(required = false) PaymentRefundRequest request,
        Authentication authentication
    ) {
        raceAccessGuard.assertCanManage(raceId);
        PaymentRefundRequest effective = request == null ? new PaymentRefundRequest() : request;
        PaymentDto payment = paymentService.refund(raceId, participantId, effective.getAmount(), effective.getReason(),
            authentication.getName());
        auditEventService.recordForActor("PAYMENT_REFUNDED", authentication.getName(), "PARTICIPANT",
            participantId.toString(), "SUCCESS", Map.of());
        return ResponseEntity.ok(payment);
    }

    @PostMapping("/v${racetracker.api.version}/payments/callback/pesepay")
    public ResponseEntity<Map<String, String>> pesepayCallback(
        @RequestHeader(value = "authorization", required = false) String authorization,
        @RequestBody(required = false) String body
    ) {
        paymentService.handlePesepayCallback(authorization, body);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
