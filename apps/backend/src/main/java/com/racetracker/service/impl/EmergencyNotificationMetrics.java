package com.racetracker.service.impl;

import com.racetracker.model.EmergencyAlert;
import com.racetracker.model.EmergencyNotificationJob;
import com.racetracker.repository.EmergencyAlertRepository;
import com.racetracker.repository.EmergencyNotificationJobRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Component
public class EmergencyNotificationMetrics {

    public EmergencyNotificationMetrics(MeterRegistry registry, EmergencyNotificationJobRepository jobs,
                                        EmergencyAlertRepository alerts) {
        Gauge.builder("emergency.notification.backlog", jobs,
                repository -> repository.countByStatusAndAlertStatusNot(
                    EmergencyNotificationJob.Status.PENDING, EmergencyAlert.EmergencyStatus.RESOLVED))
            .description("SOS notification jobs waiting for delivery")
            .register(registry);
        Gauge.builder("emergency.notification.overdue", jobs,
                repository -> repository.countByStatusAndCreatedAtBeforeAndAlertStatusNot(
                    EmergencyNotificationJob.Status.PENDING, OffsetDateTime.now().minusMinutes(2),
                    EmergencyAlert.EmergencyStatus.RESOLVED))
            .description("SOS notification jobs waiting more than two minutes")
            .register(registry);
        Gauge.builder("emergency.notification.exhausted", jobs,
                repository -> repository.countByStatusAndAlertStatusNot(
                    EmergencyNotificationJob.Status.EXHAUSTED, EmergencyAlert.EmergencyStatus.RESOLVED))
            .description("SOS notification jobs that exhausted delivery attempts")
            .register(registry);
        Gauge.builder("emergency.alert.escalation.required", alerts,
                repository -> repository.countByDeliveryStatusAndStatusNot(
                    EmergencyAlert.DeliveryStatus.ESCALATION_REQUIRED, EmergencyAlert.EmergencyStatus.RESOLVED))
            .description("Unresolved SOS alerts with no confirmed organizer delivery")
            .register(registry);
    }
}
