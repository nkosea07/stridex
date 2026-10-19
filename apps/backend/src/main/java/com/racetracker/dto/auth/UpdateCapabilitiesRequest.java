package com.racetracker.dto.auth;

import com.racetracker.model.UserCapabilityType;
import lombok.Getter;
import lombok.Setter;

import java.util.Set;

@Getter
@Setter
public class UpdateCapabilitiesRequest {

    private Set<UserCapabilityType> capabilities;

    private UserCapabilityType preferredCapability;
}
