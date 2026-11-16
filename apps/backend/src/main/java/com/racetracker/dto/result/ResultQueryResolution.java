package com.racetracker.dto.result;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResultQueryResolution {

    public enum Outcome { RESOLVED, REJECTED }

    @NotNull
    private Outcome outcome;

    @NotBlank
    @Size(min = 5, max = 1000)
    private String note;
}
