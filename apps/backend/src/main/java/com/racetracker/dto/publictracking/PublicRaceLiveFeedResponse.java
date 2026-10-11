package com.racetracker.dto.publictracking;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class PublicRaceLiveFeedResponse {
    private List<PublicRaceAthleteSummaryResponse> items = new ArrayList<>();
    private String nextCursor;
    private OffsetDateTime refreshedAt;
}
