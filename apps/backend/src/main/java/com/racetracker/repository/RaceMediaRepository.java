package com.racetracker.repository;

import com.racetracker.model.RaceMedia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RaceMediaRepository extends JpaRepository<RaceMedia, UUID> {

    List<RaceMedia> findByRaceIdOrderByCreatedAtDesc(UUID raceId);

    List<RaceMedia> findByRaceIdAndStatusOrderByCreatedAtDesc(UUID raceId, RaceMedia.MediaStatus status);

    Optional<RaceMedia> findByRaceIdAndId(UUID raceId, UUID id);

    long countByRaceIdAndStatus(UUID raceId, RaceMedia.MediaStatus status);

    @Modifying
    @Query("""
        delete from RaceMedia m
        where m.uploadedBy = :userId
           or m.participant.id in (select p.id from Participant p where p.user.id = :userId)
    """)
    int deletePersonalMediaForUser(@Param("userId") UUID userId);
}
