package com.racetracker.dto.result;

import java.util.UUID;

public record PublicRaceResultDto(
    UUID participantId,
    String participantName,
    String bibNumber,
    String status,
    String totalTime,
    String finishTime,
    String category,
    Integer overallPosition,
    Integer genderPosition,
    Integer categoryPosition
) { }
