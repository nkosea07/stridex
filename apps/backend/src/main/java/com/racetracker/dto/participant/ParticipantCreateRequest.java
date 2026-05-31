package com.racetracker.dto.participant;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
public class ParticipantCreateRequest {
    private UUID categoryId;

    @NotBlank
    private String firstName;

    @NotBlank
    private String lastName;

    @Email
    @NotBlank
    private String email;

    private String phone;
    private LocalDate dateOfBirth;
    private String gender;
    private String nationality;
    private String bibNumber;
    private String rfidTag;
    private String emergencyContactName;
    private String emergencyContactPhone;
    private String emergencyContactRelation;
    private String bloodType;
    private String medicalConditions;
    private String allergies;
    private String clubName;
    private String teamName;
    private String registrationSource;
    private Boolean isTrackingEnabled;
}
