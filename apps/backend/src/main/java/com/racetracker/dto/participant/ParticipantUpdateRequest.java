package com.racetracker.dto.participant;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class ParticipantUpdateRequest {
    private UUID categoryId;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private LocalDate dateOfBirth;
    private String gender;
    private String nationality;
    private String bibNumber;
    private String rfidTag;
    private String status;
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
    private List<ParticipantCredentialRequest> credentials;
}
