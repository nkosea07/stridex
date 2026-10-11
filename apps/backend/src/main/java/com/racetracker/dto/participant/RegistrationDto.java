package com.racetracker.dto.participant;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
public class RegistrationDto {
    private UUID id;
    private UUID raceId;
    private UUID userId;
    private UUID categoryId;
    private String bibNumber;
    private String status;
    private OffsetDateTime registrationDate;
    private EmergencyContact emergencyContact;
    private String shirtSize;
    private String paymentStatus;
    private BigDecimal paymentAmount;
    private UUID teamId;
    private String teamName;
    private String rejectionReason;
    private Integer waitlistPosition;

    @Getter
    @Setter
    public static class EmergencyContact {
        private String name;
        private String phone;
        private String relationship;
    }
}
