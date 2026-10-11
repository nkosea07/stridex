package com.racetracker.repository;

import com.racetracker.model.RfidReadEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RfidReadEventRepository extends JpaRepository<RfidReadEvent, UUID> {

    Optional<RfidReadEvent> findByReaderIdAndSourceEventId(UUID readerId, String sourceEventId);

    @EntityGraph(attributePaths = {"reader", "reader.race", "reader.checkpoint", "matchedParticipant", "checkpointRecord"})
    Optional<RfidReadEvent> findWithRelationsById(UUID id);

    Optional<RfidReadEvent> findFirstByReaderIdAndSourceEventIdAndIdNotOrderByReceivedAtAscIdAsc(
        UUID readerId,
        String sourceEventId,
        UUID id
    );

    List<RfidReadEvent> findTop100ByStatusOrderByReceivedAtAsc(RfidReadEvent.RfidReadEventStatus status);

    long countByStatus(RfidReadEvent.RfidReadEventStatus status);

    long countByRaceId(UUID raceId);

    long countByRaceIdAndStatus(UUID raceId, RfidReadEvent.RfidReadEventStatus status);

    @Query("select min(event.receivedAt) from RfidReadEvent event where event.race.id = :raceId and event.status = :status")
    OffsetDateTime findOldestReceivedAtByRaceIdAndStatus(
        @Param("raceId") UUID raceId,
        @Param("status") RfidReadEvent.RfidReadEventStatus status
    );

    @Query(
        value = """
            select cast(extract(epoch from avg(received_at - read_at)) * 1000 as bigint)
            from rfid_read_events
            where race_id = :raceId
            """,
        nativeQuery = true
    )
    Long findAverageIngressLagMillis(@Param("raceId") UUID raceId);

    @Query(
        value = """
            select date_trunc('hour', received_at) as bucket,
                   count(*) as total,
                   count(*) filter (where status = 'ACCEPTED') as accepted,
                   count(*) filter (where status = 'DUPLICATE') as duplicate,
                   count(*) filter (where status = 'UNKNOWN_TAG') as unknown_credential,
                   count(*) filter (where status = 'ERROR') as error
            from rfid_read_events
            where race_id = :raceId
            group by bucket
            order by bucket
            """,
        nativeQuery = true
    )
    List<Object[]> findEventTrendByRaceId(@Param("raceId") UUID raceId);
}
