package com.racetracker.dto.notification;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NotificationTemplateRequest {

    @NotBlank
    private String code;

    private String channel = "IN_APP";

    private String locale = "en";

    @NotBlank
    private String subject;

    @NotBlank
    private String body;

    private Boolean enabled = true;

    private String metaTemplateName;

    private String languageCode = "en_US";
}
