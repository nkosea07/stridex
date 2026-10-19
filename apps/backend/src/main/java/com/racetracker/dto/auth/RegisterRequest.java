package com.racetracker.dto.auth;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.racetracker.model.UserCapabilityType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
public class RegisterRequest {
    @Email
    @NotBlank
    private String email;

    @NotBlank
    @Size(min = 8, max = 128)
    private String password;

    @NotBlank
    private String firstName;

    @NotBlank
    private String lastName;

    private String phone;

    /**
     * Legacy singular input retained for older callers. New clients send
     * experiences as a set so spectator-only and dual-capability onboarding
     * are represented without changing the legacy role column.
     */
    @JsonAlias({"capability", "preferredCapability"})
    private UserCapabilityType experience;

    private Set<UserCapabilityType> experiences;

    @JsonIgnore
    private boolean createNormalCapabilities = true;

    private Boolean privacyPolicyAccepted;

    @Size(max = 40)
    private String privacyPolicyVersion;
}
