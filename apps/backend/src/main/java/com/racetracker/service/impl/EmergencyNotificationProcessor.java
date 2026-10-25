package com.racetracker.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.racetracker.dto.notification.NotificationDto;
import com.racetracker.model.EmergencyAlert;
import com.racetracker.model.EmergencyNotificationJob;
import com.racetracker.model.Notification;
import com.racetracker.model.NotificationDelivery;
import com.racetracker.model.Participant;
import com.racetracker.model.User;
import com.racetracker.notification.NotificationMessage;
import com.racetracker.repository.EmergencyAlertRepository;
import com.racetracker.repository.EmergencyNotificationJobRepository;
import com.racetracker.repository.NotificationRepository;
import com.racetracker.repository.UserRepository;
import com.racetracker.service.NotificationChannel;
import com.racetracker.websocket.TrackingLivePublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Delivers persisted SOS notification jobs with leases, retries, and an escalation state so an
 * emergency alert can never depend on an in-request channel send. Channel I/O runs outside the
 * database transaction; job claims and state transitions are transactional.
 */
@Slf4j
@Component
public class EmergencyNotificationProcessor {

    private static final int BATCH_SIZE = 50;
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LEASE_DURATION = Duration.ofMinutes(2);

    private final EmergencyNotificationJobRepository jobRepository;
    private final EmergencyAlertRepository alertRepository;
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final Map<NotificationDelivery.Channel, NotificationChannel> channels;
    private final TrackingLivePublisher trackingLivePublisher;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public EmergencyNotificationProcessor(
        EmergencyNotificationJobRepository jobRepository,
        EmergencyAlertRepository alertRepository,
        NotificationRepository notificationRepository,
        UserRepository userRepository,
        List<NotificationChannel> channels,
        TrackingLivePublisher trackingLivePublisher,
        ObjectMapper objectMapper,
        PlatformTransactionManager transactionManager
    ) {
        this.jobRepository = jobRepository;
        this.alertRepository = alertRepository;
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.channels = channels.stream()
            .collect(Collectors.toUnmodifiableMap(NotificationChannel::channel, Function.identity()));
        this.trackingLivePublisher = trackingLivePublisher;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Scheduled(
        fixedDelayString = "${racetracker.emergency.dispatch-interval-millis:3000}",
        initialDelayString = "${racetracker.emergency.dispatch-initial-delay-millis:5000}"
    )
    public void scheduledDispatch() {
        try {
            processBatch();
        } catch (RuntimeException ex) {
            log.error("Emergency notification dispatch failed", ex);
        }
    }

    public void processBatch() {
        UUID leaseToken = UUID.randomUUID();
        List<Claim> claims = transactionTemplate.execute(status -> claim(leaseToken));
        if (claims == null || claims.isEmpty()) {
            return;
        }
        Set<UUID> alertIds = new LinkedHashSet<>();
        for (Claim claim : claims) {
            alertIds.add(claim.alertId());
            try {
                deliver(claim);
            } catch (Exception ex) {
                log.warn("Emergency notification delivery failed; jobId={}, channel={}, errorType={}",
                    claim.jobId(), claim.channel(), ex.getClass().getSimpleName());
                transactionTemplate.executeWithoutResult(status -> markFailure(claim, leaseToken));
            }
        }
        for (UUID alertId : alertIds) {
            transactionTemplate.executeWithoutResult(status -> refreshAlertStatus(alertId));
        }
    }

    private List<Claim> claim(UUID leaseToken) {
        OffsetDateTime now = OffsetDateTime.now();
        List<UUID> ids = jobRepository.findClaimableIds(now, BATCH_SIZE);
        List<Claim> claims = new ArrayList<>(ids.size());
        for (UUID id : ids) {
            EmergencyNotificationJob job = jobRepository.findByIdForUpdate(id).orElse(null);
            if (job == null || !claimable(job, now)) {
                continue;
            }
            job.setStatus(EmergencyNotificationJob.Status.PROCESSING);
            job.setLeaseToken(leaseToken);
            job.setLeaseUntil(now.plus(LEASE_DURATION));
            job.setAttempts(job.getAttempts() + 1);
            claims.add(new Claim(job.getId(), job.getAlert().getId(), job.getChannel(), leaseToken, message(job)));
        }
        return claims;
    }

    private boolean claimable(EmergencyNotificationJob job, OffsetDateTime now) {
        if (job.getStatus() == EmergencyNotificationJob.Status.PENDING) {
            return job.getNextAttemptAt() == null || !job.getNextAttemptAt().isAfter(now);
        }
        return job.getStatus() == EmergencyNotificationJob.Status.PROCESSING
            && job.getLeaseUntil() != null && !job.getLeaseUntil().isAfter(now);
    }

    private void deliver(Claim claim) throws Exception {
        if (claim.channel() == NotificationDelivery.Channel.IN_APP) {
            Notification saved = transactionTemplate.execute(status -> persistInApp(claim));
            if (saved != null) {
                publishInApp(saved);
            }
            return;
        }
        NotificationChannel channel = channels.get(claim.channel());
        if (channel == null || !channel.isEnabled()) {
            throw new IllegalStateException("Emergency notification channel is unavailable");
        }
        channel.send(claim.message());
        transactionTemplate.executeWithoutResult(status -> markDispatched(claim));
    }

    private Notification persistInApp(Claim claim) {
        Notification notification = new Notification();
        notification.setUser(userRepository.getReferenceById(claim.message().userId()));
        notification.setTitle(claim.message().title());
        notification.setMessage(claim.message().body());
        notification.setType(claim.message().type());
        notification.setIsRead(false);
        notification.setData(serialize(claim.message().data()));
        Notification saved = notificationRepository.save(notification);
        markDispatched(claim);
        return saved;
    }

    private void publishInApp(Notification notification) {
        NotificationDto dto = new NotificationDto();
        dto.setId(notification.getId());
        dto.setTitle(notification.getTitle());
        dto.setMessage(notification.getMessage());
        dto.setType(notification.getType() != null ? notification.getType().name().toLowerCase() : null);
        dto.setTimestamp(OffsetDateTime.now());
        dto.setRead(false);
        try {
            trackingLivePublisher.publishUserAlert(notification.getUser().getId(), dto);
        } catch (RuntimeException ex) {
            log.warn("Failed to publish emergency alert over WebSocket; notificationId={}", notification.getId());
        }
    }

    private void markDispatched(Claim claim) {
        EmergencyNotificationJob job = jobRepository.findByIdForUpdate(claim.jobId()).orElse(null);
        if (job == null || !claim.leaseToken().equals(job.getLeaseToken())) {
            return;
        }
        job.setStatus(EmergencyNotificationJob.Status.DISPATCHED);
        job.setDispatchedAt(OffsetDateTime.now());
        job.setLeaseToken(null);
        job.setLeaseUntil(null);
    }

    private void markFailure(Claim claim, UUID leaseToken) {
        EmergencyNotificationJob job = jobRepository.findByIdForUpdate(claim.jobId()).orElse(null);
        if (job == null || !leaseToken.equals(job.getLeaseToken())) {
            return;
        }
        job.setLeaseToken(null);
        job.setLeaseUntil(null);
        if (job.getAttempts() >= MAX_ATTEMPTS) {
            job.setStatus(EmergencyNotificationJob.Status.EXHAUSTED);
            log.error("Emergency notification job exhausted; jobId={}, alertId={}, channel={}",
                job.getId(), claim.alertId(), claim.channel());
            return;
        }
        job.setStatus(EmergencyNotificationJob.Status.PENDING);
        long delaySeconds = Math.min(900, 30L * job.getAttempts() * job.getAttempts());
        job.setNextAttemptAt(OffsetDateTime.now().plusSeconds(delaySeconds));
    }

    private void refreshAlertStatus(UUID alertId) {
        long exhausted = jobRepository.countByAlertIdAndStatus(alertId,
            EmergencyNotificationJob.Status.EXHAUSTED);
        if (exhausted == 0) {
            long pending = jobRepository.countByAlertIdAndStatusIn(alertId,
                List.of(EmergencyNotificationJob.Status.PENDING, EmergencyNotificationJob.Status.PROCESSING));
            if (pending > 0) {
                return;
            }
        }
        EmergencyAlert alert = alertRepository.findById(alertId).orElse(null);
        if (alert == null) {
            return;
        }
        long dispatched = jobRepository.countByAlertIdAndStatus(alertId,
            EmergencyNotificationJob.Status.DISPATCHED);
        alert.setDeliveryStatus(exhausted == 0 && dispatched > 0
            ? EmergencyAlert.DeliveryStatus.DISPATCHED
            : EmergencyAlert.DeliveryStatus.ESCALATION_REQUIRED);
        alertRepository.save(alert);
    }

    private NotificationMessage message(EmergencyNotificationJob job) {
        EmergencyAlert alert = job.getAlert();
        Participant participant = alert.getParticipant();
        User reporter = alert.getReportedBy();
        String who = participant != null
            ? participant.getFullName()
            : reporter != null && reporter.getFirstName() != null ? reporter.getFirstName() : "An athlete";
        String bib = participant != null && participant.getBibNumber() != null
            ? " (bib " + participant.getBibNumber() + ")" : "";
        String body = alert.getMessage() == null || alert.getMessage().isBlank()
            ? "An athlete has raised an emergency alert."
            : alert.getMessage();
        Map<String, String> data = new LinkedHashMap<>();
        data.put("emergencyAlertId", alert.getId().toString());
        data.put("raceId", alert.getRace().getId().toString());
        if (alert.getLatitude() != null && alert.getLongitude() != null) {
            data.put("latitude", alert.getLatitude().toPlainString());
            data.put("longitude", alert.getLongitude().toPlainString());
        }
        User recipient = job.getRecipient();
        return new NotificationMessage(recipient.getId(), recipient.getEmail(), recipient.getPhone(),
            Notification.NotificationType.ALERT, "EMERGENCY: " + who + bib, body, data);
    }

    private String serialize(Map<String, String> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private record Claim(UUID jobId, UUID alertId, NotificationDelivery.Channel channel,
                         UUID leaseToken, NotificationMessage message) {
    }
}
