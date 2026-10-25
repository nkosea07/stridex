package com.racetracker.controller;

import com.racetracker.dto.participant.ExternalRefundAttemptDto;
import com.racetracker.dto.participant.ExternalRefundAttemptRequest;
import com.racetracker.dto.participant.ExternalRefundReconciliationRequest;
import com.racetracker.dto.participant.PaymentDto;
import com.racetracker.dto.participant.PaymentRefundRequest;
import com.racetracker.security.RaceAccessGuard;
import com.racetracker.service.AuditEventService;
import com.racetracker.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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

    @PostMapping("/v${racetracker.api.version}/races/{raceId}/participants/{participantId}/payment/refund/attempt")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<PaymentDto> stageExternalRefundCompat(@PathVariable UUID raceId,
                                                                @PathVariable UUID participantId,
                                                                @Valid @RequestBody ExternalRefundAttemptRequest request,
                                                                Authentication authentication) {
        raceAccessGuard.assertCanManage(raceId);
        PaymentDto payment = paymentService.stageExternalRefund(raceId, participantId, request.reason(),
            authentication.getName());
        auditEventService.recordForActor("EXTERNAL_REFUND_ATTEMPT_STAGED", authentication.getName(), "PARTICIPANT",
            participantId.toString(), "SUCCESS", Map.of("raceId", raceId.toString()));
        return ResponseEntity.accepted().body(payment);
    }

    @PostMapping("/v${racetracker.api.version}/races/{raceId}/participants/{participantId}/payment/refund/attempts")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ExternalRefundAttemptDto> stageExternalRefund(@PathVariable UUID raceId,
                                                                        @PathVariable UUID participantId,
                                                                        @Valid @RequestBody ExternalRefundAttemptRequest request,
                                                                        Authentication authentication) {
        raceAccessGuard.assertCanManage(raceId);
        ExternalRefundAttemptDto attempt = paymentService.stageExternalRefundAttempt(raceId, participantId,
            request.paymentId(), request.reason(), authentication.getName());
        auditEventService.recordForActor("EXTERNAL_REFUND_ATTEMPT_STAGED", authentication.getName(), "PARTICIPANT",
            participantId.toString(), "SUCCESS", Map.of("raceId", raceId.toString(),
                "paymentId", attempt.paymentId().toString(), "amount", attempt.amount().toPlainString(),
                "currency", attempt.currency() == null ? "" : attempt.currency()));
        return ResponseEntity.accepted().body(attempt);
    }

    @GetMapping("/v${racetracker.api.version}/races/{raceId}/participants/{participantId}/payment/refund/eligible-payments")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<PaymentDto>> eligibleRefundPayments(@PathVariable UUID raceId,
                                                                     @PathVariable UUID participantId) {
        raceAccessGuard.assertCanManage(raceId);
        return ResponseEntity.ok(paymentService.eligibleRefundPayments(raceId, participantId));
    }

    @GetMapping("/v${racetracker.api.version}/races/{raceId}/payments/refund-eligible-participants")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<UUID>> eligibleRefundParticipants(@PathVariable UUID raceId) {
        raceAccessGuard.assertCanManage(raceId);
        return ResponseEntity.ok(paymentService.eligibleRefundParticipantIds(raceId));
    }

    @GetMapping("/v${racetracker.api.version}/races/{raceId}/participants/{participantId}/payment/refund/attempts")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<ExternalRefundAttemptDto>> listRefundAttempts(@PathVariable UUID raceId,
                                                                               @PathVariable UUID participantId) {
        raceAccessGuard.assertCanManage(raceId);
        return ResponseEntity.ok(paymentService.listExternalRefundAttempts(raceId, participantId));
    }

    @GetMapping("/v${racetracker.api.version}/races/{raceId}/participants/{participantId}/payment/refund/attempts/{attemptId}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ExternalRefundAttemptDto> getRefundAttempt(@PathVariable UUID raceId,
                                                                       @PathVariable UUID participantId,
                                                                       @PathVariable UUID attemptId) {
        raceAccessGuard.assertCanManage(raceId);
        return ResponseEntity.ok(paymentService.getExternalRefundAttempt(raceId, participantId, attemptId));
    }

    @PostMapping("/v${racetracker.api.version}/races/{raceId}/participants/{participantId}/payment/refund/attempts/{attemptId}/reconcile")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ExternalRefundAttemptDto> reconcileRefundAttempt(@PathVariable UUID raceId,
                                                                             @PathVariable UUID participantId,
                                                                             @PathVariable UUID attemptId,
                                                                             @Valid @RequestBody ExternalRefundReconciliationRequest request,
                                                                             Authentication authentication) {
        raceAccessGuard.assertCanManage(raceId);
        ExternalRefundAttemptDto result = paymentService.reconcileExternalRefundAttempt(raceId, participantId,
            attemptId, request.providerReference(), request.reason(),
            request.outcome() == ExternalRefundReconciliationRequest.Outcome.REFUNDED, authentication.getName());
        auditEventService.recordForActor("EXTERNAL_REFUND_RECONCILED", authentication.getName(), "PARTICIPANT",
            participantId.toString(), "SUCCESS", Map.of("outcome", request.outcome().name(),
                "attemptId", attemptId.toString(), "providerReference", request.providerReference()));
        return ResponseEntity.ok(result);
    }

    @PostMapping("/v${racetracker.api.version}/races/{raceId}/participants/{participantId}/payment/refund/reconcile")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<PaymentDto> reconcileExternalRefund(@PathVariable UUID raceId, @PathVariable UUID participantId,
                                                                @Valid @RequestBody ExternalRefundReconciliationRequest request,
                                                                Authentication authentication) {
        raceAccessGuard.assertCanManage(raceId);
        PaymentDto payment = paymentService.reconcileExternalRefund(raceId, participantId,
            request.providerReference(), request.reason(),
            request.outcome() == ExternalRefundReconciliationRequest.Outcome.REFUNDED,
            authentication.getName());
        auditEventService.recordForActor("EXTERNAL_REFUND_RECONCILED", authentication.getName(), "PARTICIPANT",
            participantId.toString(), "SUCCESS", Map.of("outcome", request.outcome().name()));
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
