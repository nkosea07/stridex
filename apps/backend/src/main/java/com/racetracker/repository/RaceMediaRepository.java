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

    @Query("""
        select m from RaceMedia m left join m.participant p
        where m.race.id = :raceId and m.status = com.racetracker.model.RaceMedia.MediaStatus.APPROVED
          and ((p is not null and p.mediaPublicationConsentAt is not null)
            or (p is null and m.nonPersonPublicationAttestedAt is not null))
        order by m.createdAt desc
    """)
    List<RaceMedia> findPublicVisibleByRaceId(@Param("raceId") UUID raceId);

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
