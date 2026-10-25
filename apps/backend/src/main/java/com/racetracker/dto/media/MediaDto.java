package com.racetracker.dto.media;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
public class MediaDto {
    private UUID id;
    private UUID raceId;
    private UUID participantId;
    private String mediaType;
    private String url;
    private String thumbnailUrl;
    private Integer width;
    private Integer height;
    private String caption;
    private String status;
    private Boolean participantConsent;
    private Boolean nonPersonAttested;
    private Boolean publiclyVisible;
    private OffsetDateTime createdAt;
}
