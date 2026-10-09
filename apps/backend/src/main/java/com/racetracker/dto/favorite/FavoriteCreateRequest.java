package com.racetracker.dto.favorite;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class FavoriteCreateRequest {

    @NotNull
    private UUID raceId;

    @NotNull
    private UUID participantId;
}
