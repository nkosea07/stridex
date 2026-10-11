package com.racetracker.service;

import com.racetracker.dto.analytics.DemographicsDto;
import com.racetracker.dto.analytics.RaceAnalyticsSummaryDto;
import com.racetracker.dto.analytics.RegistrationPointDto;

import java.util.List;
import java.util.UUID;

public interface AnalyticsService {

    RaceAnalyticsSummaryDto summary(UUID raceId);

    DemographicsDto demographics(UUID raceId);

    List<RegistrationPointDto> registrationSeries(UUID raceId, int days);
}
