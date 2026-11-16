package com.racetracker.repository;

import com.racetracker.model.CheckpointRecord;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CheckpointRecordRepository extends JpaRepository<CheckpointRecord, UUID> {

    long countByCheckpointId(UUID checkpointId);

    boolean existsByParticipantId(UUID participantId);

    @Query(value = "select * from checkpoint_records where id = :id and race_id = :raceId and checkpoint_id = :checkpointId "
        + "and voided_at is not null", nativeQuery = true)
    Optional<CheckpointRecord> findVoided(@Param("raceId") UUID raceId, @Param("checkpointId") UUID checkpointId,
                                          @Param("id") UUID id);

    @Query(value = "select * from checkpoint_records where race_id = :raceId and checkpoint_id = :checkpointId "
        + "and voided_at is not null order by voided_at desc limit 500", nativeQuery = true)
    List<CheckpointRecord> findVoidedByCheckpoint(@Param("raceId") UUID raceId, @Param("checkpointId") UUID checkpointId);

    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update checkpoint_records set pass_number = pass_number + 100000 "
        + "where participant_id = :participantId and checkpoint_id = :checkpointId and voided_at is null", nativeQuery = true)
    int parkActivePasses(@Param("participantId") UUID participantId, @Param("checkpointId") UUID checkpointId);

    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update checkpoint_records cr set pass_number = ranked.position from ("
        + "select id, row_number() over (order by coalesce(adjusted_recorded_at, recorded_at), recorded_at, id) as position "
        + "from checkpoint_records where participant_id = :participantId and checkpoint_id = :checkpointId "
        + "and voided_at is null) ranked where cr.id = ranked.id", nativeQuery = true)
    int renumberActivePasses(@Param("participantId") UUID participantId, @Param("checkpointId") UUID checkpointId);

    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update checkpoint_records set voided_at = null, voided_by = null, void_reason = null, "
        + "pass_number = :passNumber where id = :id and voided_at is not null", nativeQuery = true)
    int restore(@Param("id") UUID id, @Param("passNumber") int passNumber);

    long countByParticipantIdAndCheckpointId(UUID participantId, UUID checkpointId);

    @Query(value = "select exists(select 1 from checkpoint_records where participant_id = :participantId)", nativeQuery = true)
    boolean existsIncludingVoidedByParticipantId(@Param("participantId") UUID participantId);

    @Query(value = "select exists(select 1 from checkpoint_records where checkpoint_id = :checkpointId)", nativeQuery = true)
    boolean existsIncludingVoidedByCheckpointId(@Param("checkpointId") UUID checkpointId);

    @Query("""
        select r from CheckpointRecord r join fetch r.participant p left join p.wave w
        where r.race.id = :raceId
          and r.recordMethod = com.racetracker.model.CheckpointRecord.RecordMethod.RFID
          and r.adjustedRecordedAt is null
          and r.recordedAt < :gate
          and w is null
        """)
    List<CheckpointRecord> findAutomaticBeforeGateWithoutWave(@Param("raceId") UUID raceId,
                                                             @Param("gate") OffsetDateTime gate);

    @Query("""
        select r from CheckpointRecord r join fetch r.participant p join p.wave w
        where r.race.id = :raceId
          and r.recordMethod = com.racetracker.model.CheckpointRecord.RecordMethod.RFID
          and r.adjustedRecordedAt is null
          and r.recordedAt < :gate
          and w.id = :waveId
        """)
    List<CheckpointRecord> findAutomaticBeforeGateInWave(@Param("raceId") UUID raceId, @Param("waveId") UUID waveId,
                                                        @Param("gate") OffsetDateTime gate);

    @EntityGraph(attributePaths = {"participant", "checkpoint", "race", "recordedBy"})
    Optional<CheckpointRecord> findFirstByParticipantIdAndCheckpointIdOrderByPassNumberAsc(UUID participantId, UUID checkpointId);

    List<CheckpointRecord> findByParticipantIdAndCheckpointIdOrderByPassNumberAsc(UUID participantId, UUID checkpointId);

    Optional<CheckpointRecord> findByRaceIdAndCheckpointIdAndId(UUID raceId, UUID checkpointId, UUID id);

    @EntityGraph(attributePaths = {"participant", "checkpoint", "race", "recordedBy"})
    List<CheckpointRecord> findByRaceIdAndCheckpointIdOrderByRecordedAtAsc(UUID raceId, UUID checkpointId);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"participant", "recordedBy"})
    List<CheckpointRecord> findByRaceIdAndCheckpointIdOrderByCreatedAtDesc(UUID raceId, UUID checkpointId,
                                                                          org.springframework.data.domain.Pageable pageable);

    @EntityGraph(attributePaths = {"participant", "checkpoint", "race", "recordedBy"})
    List<CheckpointRecord> findByRaceIdOrderByRecordedAtDesc(UUID raceId);

    @EntityGraph(attributePaths = {"participant", "checkpoint", "race", "recordedBy"})
    Optional<CheckpointRecord> findTopByParticipantIdAndRaceIdOrderByRecordedAtDesc(UUID participantId, UUID raceId);

    @EntityGraph(attributePaths = {"participant", "checkpoint", "race", "recordedBy"})
    List<CheckpointRecord> findByParticipantIdAndRaceIdOrderByRecordedAtAsc(UUID participantId, UUID raceId);

    @EntityGraph(attributePaths = {"participant", "checkpoint", "race", "recordedBy"})
    List<CheckpointRecord> findByRaceIdAndParticipantIdInOrderByRecordedAtAsc(UUID raceId, List<UUID> participantIds);

    @EntityGraph(attributePaths = {"participant", "checkpoint", "race", "recordedBy"})
    List<CheckpointRecord> findByParticipantIdInOrderByRecordedAtAsc(List<UUID> participantIds);

    @Query("""
        select count(c)
        from CheckpointRecord c
        where c.checkpoint.checkpointType = 'FINISH'
          and c.recordedAt >= :since
    """)
    long countFinishRecordsSince(@Param("since") OffsetDateTime since);

    @Query("""
        select count(c)
        from CheckpointRecord c
        where c.checkpoint.checkpointType = 'FINISH'
          and c.recordedAt >= :since
          and c.race.id in (select managed.id from Race managed
              left join managed.createdBy owner
              left join managed.admins assignedAdmin
              where lower(owner.email) = lower(:email) or lower(assignedAdmin.email) = lower(:email))
        """)
    long countFinishRecordsSinceInRacesManagedBy(@Param("email") String email, @Param("since") OffsetDateTime since);

    @Query("select c.participant.id, count(c) from CheckpointRecord c where c.race.id = :raceId and c.isCutoffMissed = true group by c.participant.id")
    List<Object[]> countCutoffMissedByRace(@Param("raceId") UUID raceId);
}
