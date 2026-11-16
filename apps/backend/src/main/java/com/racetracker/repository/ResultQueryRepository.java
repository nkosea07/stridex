package com.racetracker.repository;

import com.racetracker.model.ResultQuery;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResultQueryRepository extends JpaRepository<ResultQuery, UUID> {

    @Query("select q from ResultQuery q join fetch q.participant left join fetch q.checkpoint "
        + "where q.raceId = :raceId and q.status = :status order by q.createdAt asc")
    List<ResultQuery> findByRaceAndStatus(@Param("raceId") UUID raceId, @Param("status") ResultQuery.Status status,
                                          Pageable pageable);

    @Query("select q from ResultQuery q join fetch q.participant left join fetch q.checkpoint "
        + "where q.submittedBy = :userId order by q.createdAt desc")
    List<ResultQuery> findBySubmitter(@Param("userId") UUID userId, Pageable pageable);

    long countByParticipantIdAndStatus(UUID participantId, ResultQuery.Status status);

    long countByRaceIdAndStatus(UUID raceId, ResultQuery.Status status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from ResultQuery q where q.id = :id and q.raceId = :raceId")
    Optional<ResultQuery> findByIdAndRaceIdForUpdate(@Param("id") UUID id, @Param("raceId") UUID raceId);
}
