package com.racetracker.dto.tracking;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SelfScanDecisionRequest {

    public enum Decision { APPROVE, REJECT }

    @NotNull
    private Decision decision;

    @Size(max = 500)
    private String note;
}
