package com.racetracker.dto.participant;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PenaltyRequest {

    @NotNull
    private Integer seconds;

    @jakarta.validation.constraints.Size(max = 500)
    private String reason;
}
