package com.racetracker.repository;

import com.racetracker.model.AuditEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface AuditEventRepository extends Repository<AuditEvent, UUID> {

    AuditEvent save(AuditEvent event);

    @Query("""
        select event from AuditEvent event
        where (:action is null or event.action = :action)
          and (:outcome is null or event.outcome = :outcome)
          and (:actorUserId is null or event.actorUserId = :actorUserId)
        """)
    Page<AuditEvent> search(
        @Param("action") String action,
        @Param("outcome") String outcome,
        @Param("actorUserId") UUID actorUserId,
        Pageable pageable
    );
}
