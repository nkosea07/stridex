package com.racetracker.dto.auth;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrganizerApplicationDecisionRequest {

    @NotNull
    private Decision decision;

    @Size(max = 2000)
    private String reviewNotes;

    public enum Decision {
        APPROVE, REJECT
    }
}
