package com.racetracker.dto.publictracking;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class TrackPointDto {
    private BigDecimal lat;
    private BigDecimal lng;
    private String time;
    private BigDecimal speed;
}
