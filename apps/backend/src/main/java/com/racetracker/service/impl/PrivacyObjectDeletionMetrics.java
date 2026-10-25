package com.racetracker.service.impl;

import com.racetracker.model.PrivacyObjectDeletionJob;
import com.racetracker.repository.AccountErasureRepository;
import com.racetracker.repository.PrivacyObjectDeletionJobRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Component
public class PrivacyObjectDeletionMetrics {

    public PrivacyObjectDeletionMetrics(MeterRegistry registry, PrivacyObjectDeletionJobRepository jobs,
                                        AccountErasureRepository erasures) {
        Gauge.builder("privacy.object.cleanup.overdue.jobs", jobs,
                repository -> repository.countByStatusNotAndCreatedAtBefore(
                    PrivacyObjectDeletionJob.Status.COMPLETED, OffsetDateTime.now().minusHours(1)))
            .description("Unfinished privacy object deletions older than one hour")
            .register(registry);
        Gauge.builder("privacy.erasure.notice.overdue", erasures,
                repository -> repository.countByFinalizedAtBeforeAndNotificationDispatchedAtIsNull(
                    OffsetDateTime.now().minusHours(1)))
            .description("Unconfirmed account erasure completion notices older than one hour")
            .register(registry);
        Gauge.builder("privacy.erasure.finalization.failed", erasures,
                AccountErasureRepository::countByFinalizationErrorCodeIsNotNull)
            .description("Account erasures or legacy scrubs waiting after finalization failure")
            .register(registry);
    }
}
