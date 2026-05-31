package com.racetracker.dto.participant;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ParticipantImportResponse {
    private int imported;
    private int skipped;
}
