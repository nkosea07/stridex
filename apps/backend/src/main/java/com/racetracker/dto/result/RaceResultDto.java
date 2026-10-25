package com.racetracker.dto.result;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class RaceResultDto {
    private UUID id;
    private UUID participantId;
    private UUID raceId;
    private UUID categoryId;

    // Web contract fields
    private String gunTime;
    private String chipTime;
    private Integer overallPosition;
    private Integer genderPosition;
    private Integer categoryPosition;
    private String ageGroup;
    private Integer ageGroupPosition;
    private String medal;
    private String qualificationStatus;
    private String averagePace;
    private Double averageSpeed;
    @JsonProperty("isOfficial")
    private boolean isOfficial;
    @JsonProperty("isDisqualified")
    private boolean isDisqualified;
    private String disqualificationReason;
    private ParticipantResultDto participant;

    // Mobile contract fields
    private String bibNumber;
    private String category;
    private String startTime;
    private String finishTime;
    private String totalTime;
    private Integer penaltySeconds;
    private boolean certificateAvailable;
    private List<ResultSplitDto> splits = new ArrayList<>();
    private String status;
}
