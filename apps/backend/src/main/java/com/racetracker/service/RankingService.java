package com.racetracker.service;

import com.racetracker.dto.result.RankingEntryDto;

import java.util.List;
import java.util.UUID;

public interface RankingService {

    List<RankingEntryDto> teamRankings(UUID raceId);

    List<RankingEntryDto> countryRankings(UUID raceId);
}
