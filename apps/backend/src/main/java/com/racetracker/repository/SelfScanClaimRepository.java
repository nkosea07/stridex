package com.racetracker.repository;

import com.racetracker.model.SelfScanClaim;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SelfScanClaimRepository extends JpaRepository<SelfScanClaim, UUID> {

    boolean existsByParticipantIdAndCheckpointIdAndStatus(UUID participantId, UUID checkpointId,
                                                         SelfScanClaim.Status status);

    @Query("""
        select c from SelfScanClaim c
        join fetch c.participant p
        join fetch c.checkpoint cp
        where c.race.id = :raceId
          and c.status = com.racetracker.model.SelfScanClaim.Status.PENDING
          and not exists (
              select r.id from CheckpointRecord r
              where r.participant.id = p.id and r.checkpoint.id = cp.id)
        order by c.claimedAt asc, c.id asc
        """)
    List<SelfScanClaim> findOpenByRaceId(@Param("raceId") UUID raceId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from SelfScanClaim c where c.id = :id and c.race.id = :raceId")
    Optional<SelfScanClaim> findByIdAndRaceIdForUpdate(@Param("id") UUID id, @Param("raceId") UUID raceId);
}
