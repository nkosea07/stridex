package com.racetracker.service;

import com.racetracker.dto.insights.ParticipantAnomalyDto;
import com.racetracker.dto.insights.RaceInsightsSummaryDto;

import java.util.List;
import java.util.UUID;

public interface RaceInsightsService {

    RaceInsightsSummaryDto summary(UUID raceId);

    List<ParticipantAnomalyDto> anomalies(UUID raceId);
}
