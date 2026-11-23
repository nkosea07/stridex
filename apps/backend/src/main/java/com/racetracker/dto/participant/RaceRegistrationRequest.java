package com.racetracker.dto.participant;

import com.racetracker.model.Participant;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class RaceRegistrationRequest {
    @NotNull
    private UUID categoryId;

    @NotNull
    private EmergencyContact emergencyContact;

    @NotNull
    @Past
    private LocalDate dateOfBirth;

    @NotNull
    private Participant.Gender gender;

    private Boolean waiverAccepted;

    private Boolean mediaPublicationConsent;

    private String bloodType;

    private String medicalConditions;

    private String allergies;

    private String shirtSize;

    private UUID teamId;

    private String clubName;

    private String teamName;

    private List<@Valid RegistrationAnswerRequest> answers;

    @Getter
    @Setter
    public static class EmergencyContact {
        private String name;
        private String phone;
        private String relationship;
    }
}
