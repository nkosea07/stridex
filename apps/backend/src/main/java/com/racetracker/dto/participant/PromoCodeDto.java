package com.racetracker.dto.participant;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
public class PromoCodeDto {
    private UUID id;
    private UUID raceId;
    private String code;
    private String discountType;
    private BigDecimal discountValue;
    private Integer maxUses;
    private Integer usedCount;
    private Boolean active;
    private OffsetDateTime expiresAt;
}
