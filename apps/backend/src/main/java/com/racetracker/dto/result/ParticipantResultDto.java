package com.racetracker.dto.result;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Participant fields needed to display a result. This is intentionally separate from
 * ParticipantDto so result consumers cannot receive registration, contact, medical,
 * payment, device, or credential data.
 */
@Getter
@Setter
public class ParticipantResultDto {
    private UUID id;
    private UUID raceId;
    private UUID categoryId;
    private String firstName;
    private String lastName;
    private String gender;
    private String nationality;
    private String bibNumber;
    private String status;
    private String clubName;
    private String teamName;
}
