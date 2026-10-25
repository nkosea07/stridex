package com.racetracker.controller;

import com.racetracker.dto.participant.ReversalCaseDto;
import com.racetracker.service.PaymentReversalReviewService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class PaymentReversalAdminController {

    private final PaymentReversalReviewService reviewService;

    public PaymentReversalAdminController(PaymentReversalReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/v${racetracker.api.version}/admin/payments/reversal-cases")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<ReversalCaseDto>> list(@RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok(reviewService.listOpenCases(limit));
    }
}
