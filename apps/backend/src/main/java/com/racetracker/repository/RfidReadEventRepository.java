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
}
