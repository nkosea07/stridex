package com.racetracker.dto.participant;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Setter
public class ParticipantDto {
    private UUID id;
    private UUID raceId;
    private UUID categoryId;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private LocalDate dateOfBirth;
    private String gender;
    private String nationality;
    private String athleteCode;

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
    private OffsetDateTime registeredAt;
    private OffsetDateTime checkedInAt;
    private Boolean isTrackingEnabled;
    private String shirtSize;
    private OffsetDateTime waiverAcceptedAt;
    private String waiverVersion;
    private OffsetDateTime approvedAt;
    private String rejectionReason;
    private Integer waitlistPosition;
    private String paymentStatus;
    private String paymentReference;
    private java.math.BigDecimal paymentAmount;
    private OffsetDateTime paymentVerifiedAt;
    private UUID teamId;
    private List<ParticipantCredentialDto> credentials;
    private List<RegistrationAnswerDto> answers;
}
