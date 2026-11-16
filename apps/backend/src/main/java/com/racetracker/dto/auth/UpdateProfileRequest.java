package com.racetracker.dto.auth;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateProfileRequest {
    private String firstName;
    private String lastName;
    private String phone;

    @jakarta.validation.constraints.Size(max = 10)
    private String preferredLocale;
}
