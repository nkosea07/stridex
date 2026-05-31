package com.racetracker.dto.participant;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class RaceRegistrationRequest {
    @NotNull
    private UUID categoryId;

    @NotNull
    private EmergencyContact emergencyContact;

    @Getter
    @Setter
    public static class EmergencyContact {
        private String name;
        private String phone;
        private String relationship;
    }
}
