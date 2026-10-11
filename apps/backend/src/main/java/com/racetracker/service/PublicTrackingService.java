package com.racetracker.service;

import com.racetracker.dto.publictracking.HeatCellDto;
import com.racetracker.dto.publictracking.PublicAthleteTrackingResponse;
import com.racetracker.dto.publictracking.PublicParticipantTrackResponse;
import com.racetracker.dto.publictracking.PublicRaceAthleteSummaryResponse;
import com.racetracker.dto.publictracking.PublicRaceHubResponse;
import com.racetracker.dto.publictracking.PublicRaceLiveFeedResponse;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface PublicTrackingService {

    PublicRaceHubResponse getRaceHub(UUID raceId);

    List<PublicRaceAthleteSummaryResponse> getLiveAthletes(UUID raceId, String query);

    PublicRaceLiveFeedResponse getLiveFeed(UUID raceId, String query, String cursor, int limit);

    PublicAthleteTrackingResponse getAthleteTracking(UUID raceId, UUID participantId);

    PublicParticipantTrackResponse getAthleteTrack(UUID raceId, UUID participantId);

    List<HeatCellDto> getRaceHeatmap(UUID raceId, OffsetDateTime from, OffsetDateTime to, Integer scale);
}
