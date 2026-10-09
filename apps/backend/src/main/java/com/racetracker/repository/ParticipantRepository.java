package com.racetracker.repository;

import com.racetracker.model.Participant;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface ParticipantRepository extends JpaRepository<Participant, UUID> {

    @EntityGraph(attributePaths = {"race", "category", "user", "credentials"})
    List<Participant> findByRaceIdOrderByRegisteredAtDesc(UUID raceId);

    @EntityGraph(attributePaths = {"race", "category", "user", "credentials"})
    List<Participant> findByRaceId(UUID raceId);

    @EntityGraph(attributePaths = {"race", "category", "user"})
    @Query("""
        select p
        from Participant p
        where p.race.id = :raceId
          and (
            lower(concat(coalesce(p.firstName, ''), ' ', coalesce(p.lastName, ''))) like lower(concat('%', :query, '%'))
            or lower(coalesce(p.bibNumber, '')) like lower(concat('%', :query, '%'))
          )
        order by p.bibNumber asc, p.registeredAt asc
    """)
    Page<Participant> searchPublicByRaceId(@Param("raceId") UUID raceId, @Param("query") String query, Pageable pageable);

    @EntityGraph(attributePaths = {"race", "category"})
    @Query("""
        select p
        from Participant p
        where p.race.id = :raceId
          and p.status = 'STARTED'
          and exists (
            select 1
            from GpsLocation g
            where g.participant.id = p.id
              and g.race.id = :raceId
          )
          and (
            :query = ''
            or lower(concat(coalesce(p.firstName, ''), ' ', coalesce(p.lastName, ''))) like lower(concat('%', :query, '%'))
            or lower(coalesce(p.bibNumber, '')) like lower(concat('%', :query, '%'))
          )
        order by p.bibNumber asc, p.registeredAt asc, p.id asc
    """)
    Page<Participant> findPublicLiveByRaceId(
        @Param("raceId") UUID raceId,
        @Param("query") String query,
        Pageable pageable
    );

    @EntityGraph(attributePaths = {"race", "category"})
    @Query("""
        select p
        from Participant p
        where p.race.id = :raceId
          and p.status = 'STARTED'
          and exists (
            select 1 from GpsLocation g
            where g.participant.id = p.id and g.race.id = :raceId
          )
          and (
            :query = ''
            or lower(concat(coalesce(p.firstName, ''), ' ', coalesce(p.lastName, ''))) like lower(concat('%', :query, '%'))
            or lower(coalesce(p.bibNumber, '')) like lower(concat('%', :query, '%'))
          )
          and (
            :lastBib is null
            or coalesce(p.bibNumber, '') > :lastBib
            or (coalesce(p.bibNumber, '') = :lastBib and coalesce(p.registeredAt, p.createdAt) > :lastRegisteredAt)
            or (coalesce(p.bibNumber, '') = :lastBib and coalesce(p.registeredAt, p.createdAt) = :lastRegisteredAt and p.id > :lastId)
          )
        order by coalesce(p.bibNumber, '') asc, coalesce(p.registeredAt, p.createdAt) asc, p.id asc
    """)
    List<Participant> findPublicLiveAfter(
        @Param("raceId") UUID raceId,
        @Param("query") String query,
        @Param("lastBib") String lastBib,
        @Param("lastRegisteredAt") OffsetDateTime lastRegisteredAt,
        @Param("lastId") UUID lastId,
        Pageable pageable
    );

    @Query(value = """
        WITH target_races AS (
            SELECT r.id
            FROM races r
            WHERE (:raceId IS NULL OR r.id = :raceId)
              AND (:publishedOnly = false OR r.results_published_at IS NOT NULL)
              AND (:userId IS NULL OR EXISTS (
                  SELECT 1 FROM participants mine WHERE mine.race_id = r.id AND mine.user_id = :userId
              ))
        ), penalties AS (
            SELECT pp.race_id, pp.participant_id, sum(pp.seconds) AS penalty_seconds
            FROM participant_penalties pp
            JOIN target_races tr ON tr.id = pp.race_id
            GROUP BY pp.race_id, pp.participant_id
        ), checkpoint_times AS (
            SELECT cr.race_id, cr.participant_id,
                   min(coalesce(cr.adjusted_recorded_at, cr.recorded_at)) FILTER (WHERE cp.checkpoint_type = 'START') AS start_checkpoint_at,
                   max(coalesce(cr.adjusted_recorded_at, cr.recorded_at)) FILTER (WHERE cp.checkpoint_type = 'FINISH') AS finish_at
            FROM checkpoint_records cr
            JOIN checkpoints cp ON cp.id = cr.checkpoint_id
            JOIN target_races tr ON tr.id = cr.race_id
            GROUP BY cr.race_id, cr.participant_id
        ), base AS (
            SELECT p.id AS participant_id, p.race_id, p.user_id, p.category_id, p.bib_number,
                   p.status::text AS status, p.gender::text AS gender,
                   coalesce(p.checked_in_at, ct.start_checkpoint_at, p.registered_at) AS start_time,
                   ct.finish_at,
                   coalesce(pen.penalty_seconds, 0) AS penalty_seconds
            FROM participants p
            JOIN target_races tr ON tr.id = p.race_id
            LEFT JOIN checkpoint_times ct ON ct.race_id = p.race_id AND ct.participant_id = p.id
            LEFT JOIN penalties pen ON pen.race_id = p.race_id AND pen.participant_id = p.id
        ), finishers AS (
            SELECT b.participant_id,
                   row_number() OVER (PARTITION BY b.race_id ORDER BY b.finish_at + make_interval(secs => b.penalty_seconds), b.participant_id) AS overall_position,
                   row_number() OVER (PARTITION BY b.race_id, b.gender ORDER BY b.finish_at + make_interval(secs => b.penalty_seconds), b.participant_id) AS gender_position,
                   row_number() OVER (PARTITION BY b.race_id, b.category_id ORDER BY b.finish_at + make_interval(secs => b.penalty_seconds), b.participant_id) AS category_position
            FROM base b
            WHERE b.status = 'FINISHED' AND b.finish_at IS NOT NULL
        )
        SELECT b.participant_id AS "participantId", b.race_id AS "raceId", b.category_id AS "categoryId",
               b.bib_number AS "bibNumber", b.status AS "status", b.gender AS "gender",
               b.start_time AS "startTime", b.finish_at AS "finishTime",
               f.overall_position AS "overallPosition", f.gender_position AS "genderPosition",
               f.category_position AS "categoryPosition"
        FROM base b
        LEFT JOIN finishers f ON f.participant_id = b.participant_id
        WHERE (:userId IS NULL OR b.user_id = :userId)
          AND (:participantId IS NULL OR b.participant_id = :participantId)
        ORDER BY f.overall_position NULLS LAST, b.finish_at NULLS LAST, b.bib_number NULLS LAST, b.participant_id
        """, nativeQuery = true)
    List<RaceResultRowProjection> findResultRows(
        @Param("raceId") UUID raceId,
        @Param("userId") UUID userId,
        @Param("publishedOnly") boolean publishedOnly,
        @Param("participantId") UUID participantId,
        Pageable pageable
    );

    @Query(value = """
        SELECT count(*)
        FROM participants p
        JOIN races r ON r.id = p.race_id
        WHERE (:raceId IS NULL OR p.race_id = :raceId)
          AND (:userId IS NULL OR p.user_id = :userId)
          AND (:publishedOnly = false OR r.results_published_at IS NOT NULL)
          AND (:userId IS NULL OR EXISTS (
              SELECT 1 FROM participants mine WHERE mine.race_id = p.race_id AND mine.user_id = :userId
          ))
        """, nativeQuery = true)
    long countResultRows(
        @Param("raceId") UUID raceId,
        @Param("userId") UUID userId,
        @Param("publishedOnly") boolean publishedOnly
    );

    @EntityGraph(attributePaths = {"race", "category", "user", "credentials"})
    List<Participant> findByIdIn(Collection<UUID> ids);

    @EntityGraph(attributePaths = {"race", "category", "user", "credentials"})
    Optional<Participant> findByRaceIdAndId(UUID raceId, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Participant p where p.race.id = :raceId and p.id = :id")
    Optional<Participant> findByRaceIdAndIdForUpdate(@Param("raceId") UUID raceId, @Param("id") UUID id);

    List<Participant> findByRaceIdAndIdIn(UUID raceId, Collection<UUID> ids);

    @EntityGraph(attributePaths = {"race", "category", "user", "credentials"})
    Optional<Participant> findByRaceIdAndUserId(UUID raceId, UUID userId);

    @EntityGraph(attributePaths = {"race", "category", "user", "credentials"})
    List<Participant> findByUserIdOrderByRegisteredAtDesc(UUID userId);

    boolean existsByRaceIdAndUserId(UUID raceId, UUID userId);

    long countByRaceId(UUID raceId);

    long countByRaceIdAndStatusIn(UUID raceId, Collection<Participant.ParticipantStatus> statuses);

    long countByRaceIdAndStatus(UUID raceId, Participant.ParticipantStatus status);

    long countByRaceIdAndTeamId(UUID raceId, UUID teamId);

    @Query("select coalesce(max(p.waitlistPosition), 0) from Participant p where p.race.id = :raceId and p.status = :status")
    Integer maxWaitlistPosition(@Param("raceId") UUID raceId, @Param("status") Participant.ParticipantStatus status);

    @EntityGraph(attributePaths = {"race", "category", "user", "credentials", "team"})
    List<Participant> findByRaceIdAndStatusOrderByRegisteredAtAsc(UUID raceId, Participant.ParticipantStatus status);

    Optional<Participant> findByRaceIdAndBibNumber(UUID raceId, String bibNumber);

    Optional<Participant> findByRaceIdAndRfidTag(UUID raceId, String rfidTag);

    @Modifying
    @Query("UPDATE Participant p SET p.totalDistance = COALESCE(p.totalDistance, 0.0) + :delta WHERE p.id = :id")
    void incrementTotalDistance(@Param("id") UUID id, @Param("delta") BigDecimal delta);
}
