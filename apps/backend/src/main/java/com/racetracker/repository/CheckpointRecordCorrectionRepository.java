package com.racetracker.repository;

import com.racetracker.model.CheckpointRecordCorrection;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CheckpointRecordCorrectionRepository extends JpaRepository<CheckpointRecordCorrection, UUID> {

    List<CheckpointRecordCorrection> findByCheckpointRecordIdOrderByCorrectedAtAsc(UUID checkpointRecordId);
}
