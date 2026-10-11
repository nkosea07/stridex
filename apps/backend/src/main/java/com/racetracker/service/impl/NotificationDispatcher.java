package com.racetracker.service.impl;

import com.racetracker.dto.notification.NotificationDto;
import com.racetracker.model.NotificationDelivery;
import com.racetracker.notification.NotificationMessage;
import com.racetracker.service.NotificationChannel;
import com.racetracker.service.NotificationPreferenceService;
import com.racetracker.websocket.TrackingLivePublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Fans a persisted notification out to every enabled channel and records the outcome.
 * Channel delivery and WebSocket publication run after the caller's transaction commits so
 * remote I/O never happens inside a transaction.
 */
@Slf4j
@Service
public class NotificationDispatcher {

    private final List<NotificationChannel> channels;
    private final NotificationDeliveryRecorder deliveryRecorder;
    private final TrackingLivePublisher trackingLivePublisher;
    private final NotificationPreferenceService preferenceService;

    public NotificationDispatcher(
        List<NotificationChannel> channels,
        NotificationDeliveryRecorder deliveryRecorder,
        TrackingLivePublisher trackingLivePublisher,
        NotificationPreferenceService preferenceService
    ) {
        this.channels = channels.stream()
            .sorted(Comparator.comparing(channel -> channel.channel().ordinal()))
            .toList();
        this.deliveryRecorder = deliveryRecorder;
        this.trackingLivePublisher = trackingLivePublisher;
        this.preferenceService = preferenceService;
    }

    public void dispatch(UUID notificationId, NotificationMessage message) {
        Runnable fanOut = () -> fanOut(notificationId, message);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    fanOut.run();
                }
            });
        } else {
            fanOut.run();
        }
    }

    private void fanOut(UUID notificationId, NotificationMessage message) {
        for (NotificationChannel channel : channels) {
            if (!channel.isEnabled()) {
                safeRecord(notificationId, channel.channel(), NotificationDelivery.Status.SKIPPED, null);
                continue;
            }
            if (!preferenceService.isChannelEnabled(message.userId(), channel.channel())) {
                safeRecord(notificationId, channel.channel(), NotificationDelivery.Status.SKIPPED, "disabled by user preference");
                continue;
            }
            try {
                channel.send(message);
                safeRecord(notificationId, channel.channel(), NotificationDelivery.Status.SENT, null);
            } catch (Exception ex) {
                log.warn("Notification channel {} failed for user {}: {}",
                    channel.channel(), message.userId(), ex.getMessage());
                safeRecord(notificationId, channel.channel(), NotificationDelivery.Status.FAILED, ex.getMessage());
            }
        }

        try {
            trackingLivePublisher.publishUserAlert(message.userId(), toDto(notificationId, message));
        } catch (RuntimeException ex) {
            log.warn("Failed to publish notification {} over WebSocket: {}", notificationId, ex.getMessage());
        }
    }

    private void safeRecord(UUID notificationId, NotificationDelivery.Channel channel,
                            NotificationDelivery.Status status, String errorMessage) {
        try {
            deliveryRecorder.record(notificationId, channel, status, errorMessage);
        } catch (RuntimeException ex) {
            log.warn("Failed to record delivery for notification {}: {}", notificationId, ex.getMessage());
        }
    }

    private NotificationDto toDto(UUID notificationId, NotificationMessage message) {
        NotificationDto dto = new NotificationDto();
        dto.setId(notificationId);
        dto.setTitle(message.title());
        dto.setMessage(message.body());
        dto.setType(message.type() != null ? message.type().name().toLowerCase() : null);
        dto.setTimestamp(OffsetDateTime.now());
        dto.setRead(false);
        dto.setData(message.data());
        return dto;
    }
}
