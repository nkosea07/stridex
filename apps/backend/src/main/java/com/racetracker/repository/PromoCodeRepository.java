package com.racetracker.repository;

import com.racetracker.model.PromoCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PromoCodeRepository extends JpaRepository<PromoCode, UUID> {

    Optional<PromoCode> findByCodeIgnoreCase(String code);

    @Query("select p from PromoCode p where p.race is null or p.race.id = :raceId order by p.createdAt desc")
    List<PromoCode> findApplicableToRace(@Param("raceId") UUID raceId);

    @Modifying
    @Query("update PromoCode p set p.usedCount = coalesce(p.usedCount, 0) + 1 "
        + "where p.id = :id and (p.maxUses is null or coalesce(p.usedCount, 0) < p.maxUses)")
    int incrementUsedCountIfAvailable(@Param("id") UUID id);
}
