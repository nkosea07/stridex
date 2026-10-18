package com.racetracker.service;

import com.racetracker.dto.participant.PromoCodeDto;
import com.racetracker.dto.participant.PromoCodeRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface PromoCodeService {

    List<PromoCodeDto> list(UUID raceId);

    PromoCodeDto create(UUID raceId, PromoCodeRequest request);

    PromoCodeDto setActive(UUID raceId, UUID id, boolean active);

    void delete(UUID raceId, UUID id);

    /**
     * Validates a promo code for a race amount and returns the discount to apply.
     */
    AppliedPromo apply(String code, UUID raceId, BigDecimal amount);

    void recordUse(UUID promoCodeId);

    void recordUseByCode(String code);

    record AppliedPromo(UUID promoCodeId, String code, BigDecimal discount) {}
}
