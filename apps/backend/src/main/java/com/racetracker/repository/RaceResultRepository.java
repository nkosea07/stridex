package com.racetracker.repository;

import com.racetracker.model.RaceResult;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RaceResultRepository extends JpaRepository<RaceResult, UUID> {

    @Modifying
    @Query("delete from RaceResult rr where rr.race.id = :raceId")
    void deleteByRaceId(@Param("raceId") UUID raceId);

    long countByRaceId(UUID raceId);

    @EntityGraph(attributePaths = {"participant", "category"})
    List<RaceResult> findByRaceIdOrderByOverallPositionAsc(UUID raceId);

    @EntityGraph(attributePaths = {"participant", "category"})
    Optional<RaceResult> findByRaceIdAndParticipantId(UUID raceId, UUID participantId);

    @EntityGraph(attributePaths = {"participant", "participant.user", "category"})
    @Query("""
        select rr
        from RaceResult rr
        where rr.race.id = :raceId
          and (:userId is null or rr.participant.user.id = :userId)
        order by rr.overallPosition asc nulls last, rr.participant.bibNumber asc nulls last
    """)
    List<RaceResult> findPageByRace(@Param("raceId") UUID raceId, @Param("userId") UUID userId, Pageable pageable);

    @Query("""
        select count(rr)
        from RaceResult rr
        where rr.race.id = :raceId
          and (:userId is null or rr.participant.user.id = :userId)
    """)
    long countPageByRace(@Param("raceId") UUID raceId, @Param("userId") UUID userId);
}
