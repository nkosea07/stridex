package com.racetracker.dto.publictracking;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class HeatCellDto {
    private Double latitude;
    private Double longitude;
    private Long weight;
}
