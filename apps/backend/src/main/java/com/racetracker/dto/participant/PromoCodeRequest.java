package com.racetracker.dto.participant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Getter
@Setter
public class PromoCodeRequest {

    @NotBlank
    private String code;

    @NotBlank
    private String discountType;

    @NotNull
    private BigDecimal discountValue;

    private Integer maxUses;
    private OffsetDateTime expiresAt;
}
