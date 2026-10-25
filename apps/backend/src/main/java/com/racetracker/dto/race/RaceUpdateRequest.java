package com.racetracker.dto.race;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

@Getter
@Setter
public class RaceUpdateRequest {
    private String name;
    private String description;
    private String raceType;
    private String timezone;
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
    private String posterUrl;
    private OffsetDateTime earlyBirdEndsAt;
    private BigDecimal earlyBirdFee;
    private BigDecimal teamFee;
    private String bibPrefix;
    private Integer bibWidth;
    private Integer medalPlaces;
    private String bibTemplate;
    private String bibAccentColor;
    private Boolean isPublicRegistration;
    private Boolean requireMedicalClearance;
    private Boolean requireApproval;
    private Boolean waitlistEnabled;
    private Boolean requireWaiver;
    private String waiverVersion;
    private String waiverText;
    private List<RaceCategoryRequest> categories;
}
