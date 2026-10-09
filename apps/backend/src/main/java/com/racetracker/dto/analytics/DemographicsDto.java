package com.racetracker.dto.analytics;

import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
public class DemographicsDto {
    private Map<String, Long> gender = new LinkedHashMap<>();
    private Map<String, Long> ageGroups = new LinkedHashMap<>();
    private Map<String, Long> countries = new LinkedHashMap<>();
    private Map<String, Long> shirtSizes = new LinkedHashMap<>();
}
