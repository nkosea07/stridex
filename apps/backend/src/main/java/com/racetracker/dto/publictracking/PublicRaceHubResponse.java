package com.racetracker.dto.publictracking;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class PublicRaceHubResponse {
    private UUID raceId;
    private String raceName;
    private String status;
    private String raceDate;
    private String city;
    private String country;
    private BigDecimal distanceKm;
    private String bannerImageUrl;
    private String logoUrl;
    private List<PublicRaceAthleteSummaryResponse> leaderboard = new ArrayList<>();
}
