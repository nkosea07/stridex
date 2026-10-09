package com.racetracker.dto.race;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

@Getter
@Setter
public class RaceCloneRequest {

    @NotBlank
    private String name;

    private LocalDate raceDate;
    private LocalTime startTime;
    private OffsetDateTime registrationOpens;
    private OffsetDateTime registrationCloses;

    private Boolean copyCategories = true;
    private Boolean copyCheckpoints = true;
    private Boolean copyRegistrationFields = true;
}
