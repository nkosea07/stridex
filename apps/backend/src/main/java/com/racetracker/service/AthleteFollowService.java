package com.racetracker.service;

import com.racetracker.dto.favorite.FavoriteAthleteDto;
import com.racetracker.dto.favorite.FavoriteCreateRequest;

import java.util.List;
import java.util.UUID;

public interface AthleteFollowService {
    List<FavoriteAthleteDto> listFavorites();
    FavoriteAthleteDto follow(FavoriteCreateRequest request);
    void unfollow(UUID raceId, UUID participantId);
}
