package com.racetracker.repository;

import com.racetracker.model.PushDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PushDeviceRepository extends JpaRepository<PushDevice, UUID> {

    Optional<PushDevice> findByToken(String token);

    List<PushDevice> findByUserId(UUID userId);

    @Transactional
    void deleteByToken(String token);

    @Transactional
    void deleteByUserId(UUID userId);
}
