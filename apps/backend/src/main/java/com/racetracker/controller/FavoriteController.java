package com.racetracker.controller;

import com.racetracker.dto.auth.ApiMessageResponse;
import com.racetracker.dto.favorite.FavoriteAthleteDto;
import com.racetracker.dto.favorite.FavoriteCreateRequest;
import com.racetracker.service.AthleteFollowService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v${racetracker.api.version}/favorites")
public class FavoriteController {

    private final AthleteFollowService athleteFollowService;

    public FavoriteController(AthleteFollowService athleteFollowService) {
        this.athleteFollowService = athleteFollowService;
    }

    @GetMapping("")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<FavoriteAthleteDto>> list() {
        return ResponseEntity.ok(athleteFollowService.listFavorites());
    }

    @PostMapping("")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<FavoriteAthleteDto> follow(@Valid @RequestBody FavoriteCreateRequest request) {
        return ResponseEntity.ok(athleteFollowService.follow(request));
    }

    @DeleteMapping("/{participantId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiMessageResponse> unfollow(@PathVariable UUID participantId, @RequestParam UUID raceId) {
        athleteFollowService.unfollow(raceId, participantId);
        return ResponseEntity.ok(new ApiMessageResponse("Athlete removed from favorites"));
    }
}
