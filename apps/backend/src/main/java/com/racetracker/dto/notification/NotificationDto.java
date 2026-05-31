package com.racetracker.dto.notification;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
public class NotificationDto {
    private UUID id;
    private String title;
    private String message;
    private String type;
    private OffsetDateTime timestamp;

    @JsonProperty("read")
    private boolean read;

    private Object data;
}
