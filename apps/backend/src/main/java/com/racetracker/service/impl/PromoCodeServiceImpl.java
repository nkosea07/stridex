package com.racetracker.service.impl;

import com.racetracker.dto.participant.PromoCodeDto;
import com.racetracker.dto.participant.PromoCodeRequest;
import com.racetracker.exception.ApiException;
import com.racetracker.exception.ConflictException;
import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.model.PromoCode;
import com.racetracker.model.Race;
import com.racetracker.repository.PromoCodeRepository;
import com.racetracker.repository.RaceRepository;
import com.racetracker.service.PromoCodeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class PromoCodeServiceImpl implements PromoCodeService {

    private final PromoCodeRepository promoCodeRepository;
    private final RaceRepository raceRepository;

    public PromoCodeServiceImpl(PromoCodeRepository promoCodeRepository, RaceRepository raceRepository) {
        this.promoCodeRepository = promoCodeRepository;
        this.raceRepository = raceRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PromoCodeDto> list(UUID raceId) {
        return promoCodeRepository.findApplicableToRace(raceId).stream().map(this::toDto).toList();
    }

    @Override
    @Transactional
    public PromoCodeDto create(UUID raceId, PromoCodeRequest request) {
        String normalized = request.getCode().trim().toUpperCase(Locale.ROOT);
        if (promoCodeRepository.findByCodeIgnoreCase(normalized).isPresent()) {
            throw new ConflictException("A promo code with this code already exists");
        }
        PromoCode.DiscountType type;
        try {
            type = PromoCode.DiscountType.valueOf(request.getDiscountType().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ApiException("Invalid discount type: " + request.getDiscountType());
        }
        if (request.getDiscountValue() == null || request.getDiscountValue().signum() <= 0) {
            throw new ApiException("Discount value must be positive");
        }

        Race race = raceId == null ? null : raceRepository.findById(raceId)
            .orElseThrow(() -> new ResourceNotFoundException("Race not found"));

        PromoCode promo = PromoCode.builder()
            .race(race)
            .code(normalized)
            .discountType(type)
            .discountValue(request.getDiscountValue())
            .maxUses(request.getMaxUses())
            .usedCount(0)
            .active(true)
            .expiresAt(request.getExpiresAt())
            .build();
        return toDto(promoCodeRepository.save(promo));
    }

    @Override
    @Transactional
    public PromoCodeDto setActive(UUID id, boolean active) {
        PromoCode promo = promoCodeRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Promo code not found"));
        promo.setActive(active);
        return toDto(promoCodeRepository.save(promo));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        if (!promoCodeRepository.existsById(id)) {
            throw new ResourceNotFoundException("Promo code not found");
        }
        promoCodeRepository.deleteById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public AppliedPromo apply(String code, UUID raceId, BigDecimal amount) {
        if (code == null || code.isBlank()) {
            return null;
        }
        PromoCode promo = promoCodeRepository.findByCodeIgnoreCase(code.trim())
            .orElseThrow(() -> new ApiException("Invalid promo code"));
        if (!Boolean.TRUE.equals(promo.getActive())) {
            throw new ApiException("This promo code is no longer active");
        }
        if (promo.getExpiresAt() != null && promo.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new ApiException("This promo code has expired");
        }
        if (promo.getRace() != null && !promo.getRace().getId().equals(raceId)) {
            throw new ApiException("This promo code is not valid for this race");
        }
        if (promo.getMaxUses() != null && promo.getUsedCount() != null && promo.getUsedCount() >= promo.getMaxUses()) {
            throw new ApiException("This promo code has reached its usage limit");
        }

        BigDecimal discount = promo.getDiscountType() == PromoCode.DiscountType.PERCENT
            ? amount.multiply(promo.getDiscountValue()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
            : promo.getDiscountValue();
        if (discount.compareTo(amount) > 0) {
            discount = amount;
        }
        return new AppliedPromo(promo.getId(), promo.getCode(), discount);
    }

    @Override
    @Transactional
    public void recordUse(UUID promoCodeId) {
        promoCodeRepository.incrementUsedCountIfAvailable(promoCodeId);
    }

    @Override
    @Transactional
    public void recordUseByCode(String code) {
        if (code == null || code.isBlank()) {
            return;
        }
        promoCodeRepository.findByCodeIgnoreCase(code.trim())
            .ifPresent(promo -> promoCodeRepository.incrementUsedCountIfAvailable(promo.getId()));
    }

    private PromoCodeDto toDto(PromoCode promo) {
        PromoCodeDto dto = new PromoCodeDto();
        dto.setId(promo.getId());
        dto.setRaceId(promo.getRace() != null ? promo.getRace().getId() : null);
        dto.setCode(promo.getCode());
        dto.setDiscountType(promo.getDiscountType() != null ? promo.getDiscountType().name() : null);
        dto.setDiscountValue(promo.getDiscountValue());
        dto.setMaxUses(promo.getMaxUses());
        dto.setUsedCount(promo.getUsedCount());
        dto.setActive(promo.getActive());
        dto.setExpiresAt(promo.getExpiresAt());
        return dto;
    }
}
