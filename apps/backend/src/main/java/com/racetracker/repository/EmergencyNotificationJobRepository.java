package com.racetracker.repository;

import com.racetracker.model.EmergencyAlert;
import com.racetracker.model.EmergencyNotificationJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmergencyNotificationJobRepository extends JpaRepository<EmergencyNotificationJob, UUID> {
    @Query(value = """
        SELECT id FROM emergency_notification_jobs
        WHERE (status = 'PENDING' AND next_attempt_at <= :now)
           OR (status = 'PROCESSING' AND lease_until <= :now)
        ORDER BY next_attempt_at, created_at, id
        LIMIT :limit FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<UUID> findClaimableIds(@Param("now") OffsetDateTime now, @Param("limit") int limit);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from EmergencyNotificationJob j where j.id = :id")
    Optional<EmergencyNotificationJob> findByIdForUpdate(@Param("id") UUID id);

    long countByAlertIdAndStatusNot(UUID alertId, EmergencyNotificationJob.Status status);

    long countByAlertIdAndStatus(UUID alertId, EmergencyNotificationJob.Status status);

    long countByAlertIdAndStatusIn(UUID alertId, Collection<EmergencyNotificationJob.Status> statuses);

    long countByStatus(EmergencyNotificationJob.Status status);

    long countByStatusAndCreatedAtBefore(EmergencyNotificationJob.Status status, OffsetDateTime before);

    long countByStatusAndAlertStatusNot(EmergencyNotificationJob.Status status,
                                        EmergencyAlert.EmergencyStatus alertStatus);

    long countByStatusAndCreatedAtBeforeAndAlertStatusNot(EmergencyNotificationJob.Status status,
                                                          OffsetDateTime before,
                                                          EmergencyAlert.EmergencyStatus alertStatus);

    @Modifying
    @Query("""
        update EmergencyNotificationJob j
        set j.status = com.racetracker.model.EmergencyNotificationJob.Status.EXHAUSTED,
            j.leaseToken = null, j.leaseUntil = null
        where j.alert.id = :alertId
          and j.status in (com.racetracker.model.EmergencyNotificationJob.Status.PENDING,
                           com.racetracker.model.EmergencyNotificationJob.Status.PROCESSING)
        """)
    int cancelPendingForAlert(@Param("alertId") UUID alertId);
}
