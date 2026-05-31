package com.racetracker.dto.race;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class RaceDto {
    private UUID id;
    private String name;
    private String description;
    private String raceType;
    private String status;
    private String city;
    private String country;
    private BigDecimal startLocationLat;
    private BigDecimal startLocationLng;
    private BigDecimal finishLocationLat;
    private BigDecimal finishLocationLng;
    private LocalDate raceDate;
    private LocalTime startTime;
    private OffsetDateTime registrationOpens;
    private OffsetDateTime registrationCloses;
    private BigDecimal distanceKm;
    private Integer maxParticipants;
    private BigDecimal registrationFee;
    private String currency;
    private String bannerImageUrl;
    private String logoUrl;
    private Boolean isPublicRegistration;
    private Boolean requireMedicalClearance;
    private Integer participantCount;
    private OffsetDateTime createdAt;
    private List<RaceCategoryDto> categories;
}
