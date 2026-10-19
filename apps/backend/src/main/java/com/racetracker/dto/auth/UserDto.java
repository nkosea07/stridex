package com.racetracker.dto.auth;

import com.racetracker.model.User;
import com.racetracker.model.UserCapabilityType;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.Set;

@Getter
@Setter
public class UserDto {
    private UUID id;
    private String email;
    private String firstName;
    private String lastName;
    private User.UserRole role;
    private Set<UserCapabilityType> capabilities;
    private UserCapabilityType preferredCapability;
    private String phone;
    private Boolean isActive;
    private Boolean emailVerified;
    private OffsetDateTime createdAt;
}
