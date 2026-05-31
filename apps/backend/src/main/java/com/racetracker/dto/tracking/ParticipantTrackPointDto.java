package com.racetracker.dto.tracking;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@AllArgsConstructor
public class ParticipantTrackPointDto {
    private BigDecimal lat;
    private BigDecimal lng;
    private String time;
}
