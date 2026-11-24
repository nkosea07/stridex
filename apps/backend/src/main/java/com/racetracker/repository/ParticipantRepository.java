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

    boolean existsByRaceIdAndEmailIgnoreCase(UUID raceId, String email);

    boolean existsByRaceIdAndRfidTag(UUID raceId, String rfidTag);

    long countByWaveId(UUID waveId);

    boolean existsByRaceIdAndRfidTagAndIdNot(UUID raceId, String rfidTag, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Participant p where p.id = :id")
    Optional<Participant> findByIdForUpdate(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"race", "category", "user", "credentials"})
    List<Participant> findByRaceIdOrderByRegisteredAtDesc(UUID raceId);

    @Query("""
        select new com.racetracker.repository.ParticipantExportRow(
            p.id, p.firstName, p.lastName, p.email, p.phone, p.bibNumber,
            p.status, p.gender, p.nationality, p.registeredAt)
        from Participant p
        where p.race.id = :raceId
        order by p.registeredAt desc
    """)
    List<ParticipantExportRow> findExportRowsByRaceId(@Param("raceId") UUID raceId, Pageable pageable);

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
    @Query(value = """
        select p
        from Participant p
        where p.race.id = :raceId
          and p.status = 'STARTED'
          and (
            exists (select 1 from GpsLocation g where g.participant.id = p.id and g.race.id = :raceId)
            or exists (select 1 from CheckpointRecord r where r.participant.id = p.id and r.race.id = :raceId)
          )
        order by p.id asc
    """, countQuery = """
        select count(p)
        from Participant p
        where p.race.id = :raceId
          and p.status = 'STARTED'
          and (
            exists (select 1 from GpsLocation g where g.participant.id = p.id and g.race.id = :raceId)
            or exists (select 1 from CheckpointRecord r where r.participant.id = p.id and r.race.id = :raceId)
          )
    """)
    Page<Participant> findPublicOnCourseByRaceId(@Param("raceId") UUID raceId, Pageable pageable);

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
            SELECT r.id, r.race_date, r.started_at,
                   CASE
                       WHEN r.race_date IS NULL OR r.start_time IS NULL THEN NULL
                       ELSE (r.race_date + r.start_time) AT TIME ZONE coalesce(tz.name, 'UTC')
                   END AS scheduled_start
            FROM races r
            LEFT JOIN pg_timezone_names tz ON tz.name = r.timezone
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
                   min(coalesce(cr.adjusted_recorded_at, cr.recorded_at)) FILTER (
                       WHERE cp.checkpoint_type = 'START'
                          OR (cp.checkpoint_type = 'FINISH' AND cp.first_pass_is_start
                              AND greatest(coalesce(cr.pass_number, 1), 1) = 1)
                   ) AS start_checkpoint_at,
                   max(coalesce(cr.adjusted_recorded_at, cr.recorded_at)) FILTER (
                       WHERE cp.checkpoint_type = 'FINISH'
                         AND greatest(coalesce(cr.pass_number, 1), 1) >= greatest(coalesce(cp.expected_passes, 1), 1)
                   ) AS finish_at
            FROM checkpoint_records cr
            JOIN checkpoints cp ON cp.id = cr.checkpoint_id
            JOIN target_races tr ON tr.id = cr.race_id
            WHERE cr.voided_at IS NULL
            GROUP BY cr.race_id, cr.participant_id
        ), timed AS (
            SELECT p.id AS participant_id, p.race_id, p.user_id, p.category_id, p.bib_number,
                   p.status::text AS status, p.gender::text AS gender, p.date_of_birth, tr.race_date,
                   coalesce(ct.start_checkpoint_at, w.started_at, w.scheduled_start, tr.started_at, tr.scheduled_start) AS start_time,
                   ct.finish_at,
                   coalesce(pen.penalty_seconds, 0) AS penalty_seconds
            FROM participants p
            JOIN target_races tr ON tr.id = p.race_id
            LEFT JOIN race_waves w ON w.id = p.wave_id
            LEFT JOIN checkpoint_times ct ON ct.race_id = p.race_id AND ct.participant_id = p.id
            LEFT JOIN penalties pen ON pen.race_id = p.race_id AND pen.participant_id = p.id
            WHERE p.status::text NOT IN ('PENDING_REVIEW', 'WAITLISTED', 'REJECTED', 'WITHDRAWN')
        ), base AS (
            SELECT t.participant_id, t.race_id, t.user_id, t.category_id, t.bib_number, t.status, t.gender,
                   t.start_time, t.finish_at, t.penalty_seconds,
                   CASE
                       WHEN t.start_time IS NULL OR t.finish_at IS NULL OR t.finish_at < t.start_time THEN NULL
                       ELSE date_trunc('second', t.finish_at - t.start_time + make_interval(secs => t.penalty_seconds))
                   END AS elapsed,
                   CASE
                       WHEN t.start_time IS NULL OR t.finish_at IS NULL OR t.finish_at < t.start_time THEN t.finish_at
                   END AS untimed_finish,
                   CASE
                       WHEN t.date_of_birth IS NULL THEN 'UNKNOWN'
                       WHEN date_part('year', age(t.race_date, t.date_of_birth)) < 20 THEN 'U20'
                       WHEN date_part('year', age(t.race_date, t.date_of_birth)) < 30 THEN '20-29'
                       WHEN date_part('year', age(t.race_date, t.date_of_birth)) < 40 THEN '30-39'
                       WHEN date_part('year', age(t.race_date, t.date_of_birth)) < 50 THEN '40-49'
                       WHEN date_part('year', age(t.race_date, t.date_of_birth)) < 60 THEN '50-59'
                       WHEN date_part('year', age(t.race_date, t.date_of_birth)) < 70 THEN '60-69'
                       ELSE '70+'
                   END AS age_group
            FROM timed t
        ), finishers AS (
            SELECT b.participant_id,
                   rank() OVER (PARTITION BY b.race_id ORDER BY b.elapsed NULLS LAST, b.untimed_finish) AS overall_position,
                   rank() OVER (PARTITION BY b.race_id, b.gender ORDER BY b.elapsed NULLS LAST, b.untimed_finish) AS gender_position,
                   rank() OVER (PARTITION BY b.race_id, b.category_id ORDER BY b.elapsed NULLS LAST, b.untimed_finish) AS category_position,
                   rank() OVER (PARTITION BY b.race_id, b.age_group ORDER BY b.elapsed NULLS LAST, b.untimed_finish) AS age_group_position
            FROM base b
            WHERE b.status = 'FINISHED' AND b.finish_at IS NOT NULL
        )
        SELECT b.participant_id AS "participantId", b.race_id AS "raceId", b.category_id AS "categoryId",
               b.bib_number AS "bibNumber", b.status AS "status", b.gender AS "gender",
               b.start_time AS "startTime", b.finish_at AS "finishTime",
               b.age_group AS "ageGroup",
               f.overall_position AS "overallPosition", f.gender_position AS "genderPosition",
               f.category_position AS "categoryPosition", f.age_group_position AS "ageGroupPosition"
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
          AND p.status::text NOT IN ('PENDING_REVIEW', 'WAITLISTED', 'REJECTED', 'WITHDRAWN')
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

    boolean existsByUserIdAndStatusIn(UUID userId, Collection<Participant.ParticipantStatus> statuses);

    boolean existsByRaceIdAndBibNumber(UUID raceId, String bibNumber);

    boolean existsByRaceIdAndBibNumberAndIdNot(UUID raceId, String bibNumber, UUID id);

    boolean existsByAthleteCode(String athleteCode);

    long countByRaceId(UUID raceId);

    @Query("""
        select count(p) from Participant p
        where p.race.id in (select managed.id from Race managed
              left join managed.createdBy owner
              left join managed.admins assignedAdmin
              where lower(owner.email) = lower(:email) or lower(assignedAdmin.email) = lower(:email))
        """)
    long countInRacesManagedBy(@Param("email") String email);

    long countByRaceIdAndStatusIn(UUID raceId, Collection<Participant.ParticipantStatus> statuses);

    long countByRaceIdAndStatus(UUID raceId, Participant.ParticipantStatus status);

    long countByRaceIdAndTeamId(UUID raceId, UUID teamId);

    @Query("select coalesce(max(p.waitlistPosition), 0) from Participant p where p.race.id = :raceId and p.status = :status")
    Integer maxWaitlistPosition(@Param("raceId") UUID raceId, @Param("status") Participant.ParticipantStatus status);

    @EntityGraph(attributePaths = {"race", "category", "user", "credentials", "team"})
    List<Participant> findByRaceIdAndStatusOrderByRegisteredAtAsc(UUID raceId, Participant.ParticipantStatus status);

    List<Participant> findByRaceIdAndStatusInOrderByRegisteredAtAsc(UUID raceId, Collection<Participant.ParticipantStatus> statuses);

    List<Participant> findByRaceIdAndStatusOrderByWaitlistPositionAscRegisteredAtAsc(UUID raceId,
                                                                                     Participant.ParticipantStatus status);

    long countByRaceIdAndCategoryIdAndStatusIn(UUID raceId, UUID categoryId,
                                               Collection<Participant.ParticipantStatus> statuses);

    @Query("select p.bibNumber from Participant p where p.race.id = :raceId and p.bibNumber like :pattern escape '\\'")
    List<String> findBibNumbersByRaceIdLike(@Param("raceId") UUID raceId, @Param("pattern") String pattern);

    @Query("""
        select p from Participant p
        join fetch p.race
        left join fetch p.category
        left join fetch p.team
        where p.race.id = :raceId
          and p.status = :status
          and (p.paymentStatus is null or p.paymentStatus not in :settled)
    """)
    List<Participant> findByRaceIdAndStatusWithUnsettledPayment(@Param("raceId") UUID raceId,
                                                                @Param("status") Participant.ParticipantStatus status,
                                                                @Param("settled") Collection<Participant.PaymentStatus> settled);

    Optional<Participant> findByRaceIdAndBibNumber(UUID raceId, String bibNumber);

    Optional<Participant> findByRaceIdAndRfidTag(UUID raceId, String rfidTag);

    @Query("select lower(p.email) from Participant p where p.race.id = :raceId and p.email is not null")
    List<String> findEmailsByRaceId(@Param("raceId") UUID raceId);

    @Modifying
    @Query("UPDATE Participant p SET p.totalDistance = COALESCE(p.totalDistance, 0.0) + :delta WHERE p.id = :id")
    void incrementTotalDistance(@Param("id") UUID id, @Param("delta") BigDecimal delta);

    @Query("select p.status, count(p) from Participant p where p.race.id = :raceId group by p.status")
    List<Object[]> countByStatusGrouped(@Param("raceId") UUID raceId);

    @Query("select p.gender, count(p) from Participant p where p.race.id = :raceId group by p.gender")
    List<Object[]> countByGender(@Param("raceId") UUID raceId);

    @Query("select p.nationality, count(p) from Participant p where p.race.id = :raceId group by p.nationality")
    List<Object[]> countByNationality(@Param("raceId") UUID raceId);

    @Query("select p.shirtSize, count(p) from Participant p where p.race.id = :raceId group by p.shirtSize")
    List<Object[]> countByShirtSize(@Param("raceId") UUID raceId);

    @Query("select p.paymentStatus, count(p), coalesce(sum(p.paymentAmount), 0) from Participant p where p.race.id = :raceId group by p.paymentStatus")
    List<Object[]> paymentTotals(@Param("raceId") UUID raceId);

    @Query(value = """
        SELECT CASE
                   WHEN p.date_of_birth IS NULL THEN 'UNKNOWN'
                   WHEN date_part('year', age(r.race_date, p.date_of_birth)) < 20 THEN 'U20'
                   WHEN date_part('year', age(r.race_date, p.date_of_birth)) < 30 THEN '20-29'
                   WHEN date_part('year', age(r.race_date, p.date_of_birth)) < 40 THEN '30-39'
                   WHEN date_part('year', age(r.race_date, p.date_of_birth)) < 50 THEN '40-49'
                   WHEN date_part('year', age(r.race_date, p.date_of_birth)) < 60 THEN '50-59'
                   WHEN date_part('year', age(r.race_date, p.date_of_birth)) < 70 THEN '60-69'
                   ELSE '70+'
               END AS age_group,
               count(*)
        FROM participants p
        JOIN races r ON r.id = p.race_id
        WHERE p.race_id = :raceId
        GROUP BY age_group
        ORDER BY age_group
        """, nativeQuery = true)
    List<Object[]> countByAgeGroup(@Param("raceId") UUID raceId);

    @Query(value = """
        SELECT to_char(date_trunc('day', p.registered_at AT TIME ZONE coalesce(tz.name, 'UTC')), 'YYYY-MM-DD') AS day,
               count(*)
        FROM participants p
        JOIN races r ON r.id = p.race_id
        LEFT JOIN pg_timezone_names tz ON tz.name = r.timezone
        WHERE p.race_id = :raceId
          AND p.registered_at >= now() - make_interval(days => :days)
        GROUP BY day
        ORDER BY day
        """, nativeQuery = true)
    List<Object[]> registrationSeries(@Param("raceId") UUID raceId, @Param("days") int days);

    @EntityGraph(attributePaths = {"category", "wave"})
    @Query("""
        select p
        from Participant p
        where p.race.id = :raceId
          and (
            lower(coalesce(p.bibNumber, '')) like lower(concat(:query, '%'))
            or lower(concat(coalesce(p.firstName, ''), ' ', coalesce(p.lastName, ''))) like lower(concat('%', :query, '%'))
          )
        order by case when lower(coalesce(p.bibNumber, '')) = lower(:query) then 0 else 1 end,
          p.lastName asc, p.firstName asc, p.id asc
    """)
    List<Participant> searchForStaff(@Param("raceId") UUID raceId, @Param("query") String query, Pageable pageable);
}
