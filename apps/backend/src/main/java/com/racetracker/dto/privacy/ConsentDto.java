package com.racetracker.dto.privacy;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class ConsentDto {
    private OffsetDateTime dataProcessingConsentAt;
    private boolean marketingConsent;
}
