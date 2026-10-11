package com.racetracker.dto.publictracking;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class PublicParticipantTrackResponse {
    private List<TrackPointDto> points;
}
