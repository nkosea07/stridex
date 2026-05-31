package com.racetracker.dto.tracking;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class ParticipantTrackResponse {
    private List<ParticipantTrackPointDto> locations;
}
