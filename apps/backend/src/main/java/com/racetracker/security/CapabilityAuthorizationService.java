package com.racetracker.security;

import com.racetracker.model.UserCapabilityType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service("capabilityAuthorizationService")
public class CapabilityAuthorizationService {

    public boolean has(Authentication authentication, UserCapabilityType capability) {
        return authentication != null
            && authentication.isAuthenticated()
            && authentication.getAuthorities().stream()
                .anyMatch(authority -> ("CAP_" + capability.name()).equals(authority.getAuthority()));
    }

    public void require(UserCapabilityType capability) {
        if (!has(SecurityContextHolder.getContext().getAuthentication(), capability)) {
            throw new AccessDeniedException("You do not have the required capability");
        }
    }
}
