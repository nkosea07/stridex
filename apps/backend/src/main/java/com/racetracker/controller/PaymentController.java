package com.racetracker.controller;

import com.racetracker.dto.participant.PaymentDto;
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

    public PaymentController(PaymentService paymentService, AuditEventService auditEventService) {
        this.paymentService = paymentService;
        this.auditEventService = auditEventService;
    }

    @PostMapping("/v${racetracker.api.version}/races/{raceId}/participants/{participantId}/payment/checkout")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL', 'ATHLETE')")
    public ResponseEntity<PaymentDto> startCheckout(
        @PathVariable UUID raceId,
        @PathVariable UUID participantId,
        Authentication authentication
    ) {
        PaymentDto payment = paymentService.startCheckout(raceId, participantId);
        auditEventService.recordForActor("PAYMENT_CHECKOUT_STARTED", authentication.getName(), "PARTICIPANT",
            participantId.toString(), "SUCCESS", Map.of("provider", String.valueOf(payment.getProvider())));
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
