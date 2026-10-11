package com.racetracker.dto.notification;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
public class NotificationTemplateDto {
    private UUID id;
    private String code;
    private String channel;
    private String locale;
    private String subject;
    private String body;
    private Boolean enabled;
    private OffsetDateTime updatedAt;
}
