package com.racetracker.repository;

import com.racetracker.model.AccountErasure;
import com.racetracker.model.PrivacyObjectDeletionJob;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountErasureRepository extends JpaRepository<AccountErasure, UUID> {

    long countByFinalizedAtBeforeAndNotificationDispatchedAtIsNull(OffsetDateTime cutoff);

    @Query("""
        select e.userId from AccountErasure e
        where e.finalizedAt is null
          and not exists (
              select j.id from PrivacyObjectDeletionJob j
              where j.userId = e.userId and j.status <> :completed
          )
        order by e.stagedAt, e.userId
    """)
    List<UUID> findReadyToFinalize(@Param("completed") PrivacyObjectDeletionJob.Status completed,
                                   Pageable pageable);

    @Query(value = """
        SELECT user_id FROM account_erasures
        WHERE finalized_at IS NOT NULL AND notification_dispatched_at IS NULL
          AND (notification_lease_until IS NULL OR notification_lease_until <= :now)
        ORDER BY finalized_at, user_id
        LIMIT :limit FOR UPDATE SKIP LOCKED
    """, nativeQuery = true)
    List<UUID> findNotificationClaims(@Param("now") OffsetDateTime now, @Param("limit") int limit);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from AccountErasure e where e.userId = :userId")
    Optional<AccountErasure> findByUserIdForUpdate(@Param("userId") UUID userId);
}
