package com.racetracker.service;

import com.racetracker.exception.ApiException;
import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.model.PromoCode;
import com.racetracker.repository.PromoCodeRepository;
import com.racetracker.repository.RaceRepository;
import com.racetracker.service.impl.PromoCodeServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromoCodeServiceImplTest {

    @Mock PromoCodeRepository promoCodeRepository;
    @Mock RaceRepository raceRepository;

    private PromoCodeServiceImpl service() {
        return new PromoCodeServiceImpl(promoCodeRepository, raceRepository);
    }

    private PromoCode promo(PromoCode.DiscountType type, String value) {
        return PromoCode.builder()
            .id(UUID.randomUUID())
            .code("SAVE10")
            .discountType(type)
            .discountValue(new BigDecimal(value))
            .usedCount(0)
            .active(true)
            .build();
    }

    @Test
    void setActiveRejectsPromoFromAnotherRace() {
        UUID raceId = UUID.randomUUID();
        UUID promoId = UUID.randomUUID();
        when(promoCodeRepository.findByRaceIdAndId(raceId, promoId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().setActive(raceId, promoId, false))
            .isInstanceOf(ResourceNotFoundException.class);

        verify(promoCodeRepository, never()).save(any(PromoCode.class));
    }

    @Test
    void deleteRejectsGlobalOrAnotherRacesPromo() {
        UUID raceId = UUID.randomUUID();
        UUID promoId = UUID.randomUUID();
        when(promoCodeRepository.findByRaceIdAndId(raceId, promoId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().delete(raceId, promoId))
            .isInstanceOf(ResourceNotFoundException.class);

        verify(promoCodeRepository, never()).delete(any(PromoCode.class));
    }

    @Test
    void appliesPercentDiscount() {
        when(promoCodeRepository.findByCodeIgnoreCase("SAVE10")).thenReturn(Optional.of(promo(PromoCode.DiscountType.PERCENT, "10")));

        var applied = service().apply("SAVE10", UUID.randomUUID(), new BigDecimal("100.00"));

        assertThat(applied.discount()).isEqualByComparingTo("10.00");
    }

    @Test
    void fixedDiscountIsCappedAtAmount() {
        when(promoCodeRepository.findByCodeIgnoreCase("SAVE10")).thenReturn(Optional.of(promo(PromoCode.DiscountType.FIXED, "150")));

        var applied = service().apply("SAVE10", UUID.randomUUID(), new BigDecimal("100.00"));

        assertThat(applied.discount()).isEqualByComparingTo("100.00");
    }

    @Test
    void expiredCodeIsRejected() {
        PromoCode code = promo(PromoCode.DiscountType.PERCENT, "10");
        code.setExpiresAt(OffsetDateTime.now().minusDays(1));
        when(promoCodeRepository.findByCodeIgnoreCase("SAVE10")).thenReturn(Optional.of(code));

        assertThatThrownBy(() -> service().apply("SAVE10", UUID.randomUUID(), new BigDecimal("100.00")))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("expired");
    }

    @Test
    void usageLimitIsEnforced() {
        PromoCode code = promo(PromoCode.DiscountType.PERCENT, "10");
        code.setMaxUses(5);
        code.setUsedCount(5);
        when(promoCodeRepository.findByCodeIgnoreCase("SAVE10")).thenReturn(Optional.of(code));

        assertThatThrownBy(() -> service().apply("SAVE10", UUID.randomUUID(), new BigDecimal("100.00")))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("usage limit");
    }
}
