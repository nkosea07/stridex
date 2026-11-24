package com.racetracker.dto.participant;

import com.racetracker.model.Participant;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
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
    @Valid
    private EmergencyContact emergencyContact;

    @NotNull
    @Past
    private LocalDate dateOfBirth;

    @NotNull
    private Participant.Gender gender;

    private Boolean waiverAccepted;

    private Boolean mediaPublicationConsent;

    @Size(max = 5)
    private String bloodType;

    @Size(max = 2000)
    private String medicalConditions;

    @Size(max = 2000)
    private String allergies;

    @Size(max = 20)
    private String shirtSize;

    private UUID teamId;

    @Size(max = 200)
    private String clubName;

    @Size(max = 200)
    private String teamName;

    private List<@Valid RegistrationAnswerRequest> answers;

    @Getter
    @Setter
    public static class EmergencyContact {
        @NotBlank
        @Size(max = 200)
        private String name;

        @NotBlank
        @Size(max = 20)
        private String phone;

        @Size(max = 50)
        private String relationship;
    }
}
