package com.racetracker.repository;

import com.racetracker.model.RfidReadEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RfidReadEventRepository extends JpaRepository<RfidReadEvent, UUID> {

    Optional<RfidReadEvent> findByReaderIdAndSourceEventId(UUID readerId, String sourceEventId);
}
