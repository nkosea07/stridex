package com.racetracker.service;

import com.racetracker.dto.publictracking.PublicAthleteTrackingResponse;
import com.racetracker.dto.publictracking.PublicRaceAthleteSummaryResponse;
import com.racetracker.dto.publictracking.PublicRaceHubResponse;

import java.util.List;
import java.util.UUID;

public interface PublicTrackingService {

    PublicRaceHubResponse getRaceHub(UUID raceId);

    List<PublicRaceAthleteSummaryResponse> getLiveAthletes(UUID raceId, String query);

    PublicAthleteTrackingResponse getAthleteTracking(UUID raceId, UUID participantId);
}
