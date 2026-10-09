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

    @EntityGraph(attributePaths = {"participant", "checkpoint", "race", "recordedBy"})
    Optional<CheckpointRecord> findByParticipantIdAndCheckpointId(UUID participantId, UUID checkpointId);

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
}
