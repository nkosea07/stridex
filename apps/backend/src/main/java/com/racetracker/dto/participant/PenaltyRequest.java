package com.racetracker.dto.participant;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PenaltyRequest {

    @NotNull
    private Integer seconds;

    private String reason;
}
