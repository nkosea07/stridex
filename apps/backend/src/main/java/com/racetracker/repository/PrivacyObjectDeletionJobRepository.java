package com.racetracker.repository;

import com.racetracker.model.PrivacyObjectDeletionJob;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PrivacyObjectDeletionJobRepository extends JpaRepository<PrivacyObjectDeletionJob, UUID> {

    @Modifying
    @Query(value = """
        INSERT INTO privacy_object_deletion_jobs (id, user_id, storage_key, storage_scope, created_at, next_attempt_at)
        SELECT uuid_generate_v4(), :userId, storage_key,
               COALESCE(storage_scope, 'UNVERIFIED_LEGACY'), :now, :now
        FROM race_media
        WHERE uploaded_by = :userId
           OR participant_id IN (SELECT id FROM participants WHERE user_id = :userId)
        ON CONFLICT (user_id, storage_key) DO NOTHING
    """, nativeQuery = true)
    int stageOriginals(@Param("userId") UUID userId, @Param("now") OffsetDateTime now);

    @Modifying
    @Query(value = """
        INSERT INTO privacy_object_deletion_jobs (id, user_id, storage_key, storage_scope, created_at, next_attempt_at)
        SELECT uuid_generate_v4(), :userId, thumbnail_key,
               COALESCE(storage_scope, 'UNVERIFIED_LEGACY'), :now, :now
        FROM race_media
        WHERE (uploaded_by = :userId
            OR participant_id IN (SELECT id FROM participants WHERE user_id = :userId))
          AND thumbnail_key IS NOT NULL AND thumbnail_key <> ''
        ON CONFLICT (user_id, storage_key) DO NOTHING
    """, nativeQuery = true)
    int stageThumbnails(@Param("userId") UUID userId, @Param("now") OffsetDateTime now);

    @Query(value = """
        SELECT id FROM privacy_object_deletion_jobs
        WHERE (status = 'PENDING' AND next_attempt_at <= :now)
           OR (status = 'PROCESSING' AND lease_until <= :now)
        ORDER BY next_attempt_at, created_at, id
        LIMIT :limit FOR UPDATE SKIP LOCKED
    """, nativeQuery = true)
    List<UUID> findClaimableIds(@Param("now") OffsetDateTime now, @Param("limit") int limit);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from PrivacyObjectDeletionJob j where j.id = :id")
    Optional<PrivacyObjectDeletionJob> findByIdForUpdate(@Param("id") UUID id);

    long countByUserIdAndStatusNot(UUID userId, PrivacyObjectDeletionJob.Status status);

    long countByStatusNotAndCreatedAtBefore(PrivacyObjectDeletionJob.Status status, OffsetDateTime cutoff);
}
