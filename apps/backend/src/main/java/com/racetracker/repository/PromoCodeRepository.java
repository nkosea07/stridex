package com.racetracker.repository;

import com.racetracker.model.PromoCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PromoCodeRepository extends JpaRepository<PromoCode, UUID> {

    Optional<PromoCode> findByCodeIgnoreCase(String code);

    @Query("select p from PromoCode p where p.race is null or p.race.id = :raceId order by p.createdAt desc")
    List<PromoCode> findApplicableToRace(@Param("raceId") UUID raceId);
}
