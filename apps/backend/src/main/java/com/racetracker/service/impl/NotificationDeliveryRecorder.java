package com.racetracker.service.impl;

import com.racetracker.model.NotificationDelivery;
import com.racetracker.repository.NotificationDeliveryRepository;
import com.racetracker.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Records delivery attempts in their own transaction so auditing never depends on, or breaks,
 * the caller's transaction (fan-out runs after commit).
 */
@Service
public class NotificationDeliveryRecorder {

    private final NotificationDeliveryRepository deliveryRepository;
    private final NotificationRepository notificationRepository;

    public NotificationDeliveryRecorder(
        NotificationDeliveryRepository deliveryRepository,
        NotificationRepository notificationRepository
    ) {
        this.deliveryRepository = deliveryRepository;
        this.notificationRepository = notificationRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID notificationId, NotificationDelivery.Channel channel,
                       NotificationDelivery.Status status, String errorMessage) {
        record(notificationId, channel, status, errorMessage, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID notificationId, NotificationDelivery.Channel channel,
                       NotificationDelivery.Status status, String errorMessage, String providerMessageId) {
        NotificationDelivery delivery = NotificationDelivery.builder()
            .notification(notificationRepository.getReferenceById(notificationId))
            .channel(channel)
            .status(status)
            .errorMessage(errorMessage)
            .providerMessageId(providerMessageId)
            .providerStatus(providerMessageId == null ? null : "sent")
            .build();
        deliveryRepository.save(delivery);
    }
}
