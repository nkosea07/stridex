package com.racetracker.service.impl;

import com.racetracker.repository.PaymentReversalCaseRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class PaymentReconciliationMetrics {

    public PaymentReconciliationMetrics(MeterRegistry registry, PaymentReversalCaseRepository reversalCases) {
        Gauge.builder("payments.reversal.review.open", reversalCases,
                PaymentReversalCaseRepository::countByResolvedAtIsNull)
            .description("Provider reversals awaiting finance verification")
            .register(registry);
    }
}
