package com.racetracker.dto.analytics;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RegistrationPointDto {
    private String date;
    private long count;
}
