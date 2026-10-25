package com.racetracker.repository;

import com.racetracker.model.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmailIgnoreCase(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where lower(u.email) = lower(:email)")
    Optional<User> findByEmailIgnoreCaseForUpdate(@Param("email") String email);

    boolean existsByEmailIgnoreCase(String email);

    @Query("""
        select u from User u
        where u.deletionRequestedAt is not null
          and u.deletionScheduledFor is not null
          and u.deletionScheduledFor <= :cutoff
          and u.deletedAt is null
          and not exists (select e.userId from AccountErasure e where e.userId = u.id)
        order by u.deletionScheduledFor, u.id
    """)
    List<User> findDueForPurge(@Param("cutoff") OffsetDateTime cutoff, Pageable pageable);

    @Query(value = """
        SELECT u.* FROM users u
        WHERE u.deletion_scheduled_for IS NOT NULL
          AND u.deletion_scheduled_for <= :cutoff
          AND u.deleted_at IS NULL
          AND NOT EXISTS (SELECT 1 FROM account_erasures e WHERE e.user_id = u.id)
          AND (u.deletion_scheduled_for > :afterTime
               OR (u.deletion_scheduled_for = :afterTime AND u.id > :afterId))
        ORDER BY u.deletion_scheduled_for, u.id
        LIMIT :limit
    """, nativeQuery = true)
    List<User> findDueForPurgeAfter(@Param("cutoff") OffsetDateTime cutoff,
                                    @Param("afterTime") OffsetDateTime afterTime,
                                    @Param("afterId") UUID afterId,
                                    @Param("limit") int limit);
}
