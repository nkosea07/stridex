package com.racetracker.dto.privacy;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConsentUpdateRequest {
    private Boolean dataProcessingConsent;
    private Boolean marketingConsent;
}
