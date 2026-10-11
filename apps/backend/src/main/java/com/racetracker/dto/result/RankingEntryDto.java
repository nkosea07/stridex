package com.racetracker.dto.result;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RankingEntryDto {
    private String name;
    private int finishers;
    private long totalSeconds;
    private String totalTime;
    private String averageTime;
    private String bestTime;
}
