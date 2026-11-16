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

    @Query("""
        select e.id from RfidReadEvent e
        where e.status = com.racetracker.model.RfidReadEvent.RfidReadEventStatus.RECEIVED
          and e.receivedAt <= :settledBefore
          and (e.nextAttemptAt is null or e.nextAttemptAt <= :now)
        order by e.receivedAt asc
        """)
    List<UUID> findRecoverableReceivedIds(@Param("settledBefore") OffsetDateTime settledBefore,
                                         @Param("now") OffsetDateTime now,
                                         org.springframework.data.domain.Pageable pageable);

    long countByStatus(RfidReadEvent.RfidReadEventStatus status);

    boolean existsByCheckpointId(UUID checkpointId);

    List<RfidReadEvent> findByMatchedParticipantIdAndCheckpointIdAndStatusInOrderByReadAtAsc(
        UUID participantId, UUID checkpointId, java.util.Collection<RfidReadEvent.RfidReadEventStatus> statuses);

    Optional<RfidReadEvent> findFirstByMatchedParticipantIdAndCheckpointIdAndReadAtGreaterThanEqualAndStatusInOrderByReadAtAsc(
        UUID participantId, UUID checkpointId, OffsetDateTime gate,
        java.util.Collection<RfidReadEvent.RfidReadEventStatus> statuses);

    @Query("""
        select e.rfidTag, count(e), min(e.readAt), max(e.readAt)
        from RfidReadEvent e
        where e.race.id = :raceId
          and e.status = com.racetracker.model.RfidReadEvent.RfidReadEventStatus.UNKNOWN_TAG
        group by e.rfidTag
        order by count(e) desc, e.rfidTag asc
        """)
    List<Object[]> findUnmatchedTagSummary(@Param("raceId") UUID raceId, org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Modifying
    @Query("""
        update RfidReadEvent e
        set e.status = com.racetracker.model.RfidReadEvent.RfidReadEventStatus.RECEIVED,
            e.processingAttempts = 0,
            e.nextAttemptAt = null,
            e.errorMessage = null,
            e.matchedParticipant = null,
            e.checkpointRecord = null
        where e.race.id = :raceId and e.status in :statuses
        """)
    int requeueByRaceIdAndStatusIn(@Param("raceId") UUID raceId,
                                   @Param("statuses") java.util.Collection<RfidReadEvent.RfidReadEventStatus> statuses);

    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true)
    @Query(value = "update rfid_read_events set status = cast('VOIDED' as rfid_read_event_status), error_message = :message "
        + "where checkpoint_record_id = :recordId", nativeQuery = true)
    int voidForCheckpointRecord(@Param("recordId") UUID recordId, @Param("message") String message);

    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true)
    @Query(value = "update rfid_read_events set status = cast('ACCEPTED' as rfid_read_event_status), error_message = null "
        + "where checkpoint_record_id = :recordId and status = cast('VOIDED' as rfid_read_event_status)", nativeQuery = true)
    int restoreForCheckpointRecord(@Param("recordId") UUID recordId);

    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true)
    @Query(value = "update rfid_read_events set checkpoint_record_id = :newRecordId, matched_participant_id = :participantId, "
        + "status = cast('ACCEPTED' as rfid_read_event_status), error_message = :message "
        + "where checkpoint_record_id = :oldRecordId", nativeQuery = true)
    int reassignCheckpointRecord(@Param("oldRecordId") UUID oldRecordId, @Param("newRecordId") UUID newRecordId,
                                 @Param("participantId") UUID participantId, @Param("message") String message);

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
