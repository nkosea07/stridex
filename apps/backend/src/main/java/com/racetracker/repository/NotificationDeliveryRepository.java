package com.racetracker.repository;

import com.racetracker.model.NotificationDelivery;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, UUID> {

    List<NotificationDelivery> findByNotificationId(UUID notificationId);

    Optional<NotificationDelivery> findFirstByProviderMessageId(String providerMessageId);
}
