package com.racetracker.repository;

import com.racetracker.model.EmergencyAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface EmergencyAlertRepository extends JpaRepository<EmergencyAlert, UUID> {

    List<EmergencyAlert> findByRaceIdOrderByCreatedAtDesc(UUID raceId);

    List<EmergencyAlert> findByRaceIdAndStatusOrderByCreatedAtDesc(UUID raceId, EmergencyAlert.EmergencyStatus status);

    long countByDeliveryStatus(EmergencyAlert.DeliveryStatus deliveryStatus);

    @Query("""
        select count(a) from EmergencyAlert a
        where a.deliveryStatus = :deliveryStatus and a.status <> :excludedStatus
        """)
    long countByDeliveryStatusAndStatusNot(@Param("deliveryStatus") EmergencyAlert.DeliveryStatus deliveryStatus,
                                           @Param("excludedStatus") EmergencyAlert.EmergencyStatus excludedStatus);

    @Modifying
    @Query("update EmergencyAlert a set a.reportedBy = null where a.reportedBy.id = :userId")
    int anonymizeReporterByUserId(@Param("userId") UUID userId);
}
