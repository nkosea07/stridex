package com.racetracker.security;

import com.racetracker.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Collection;

/**
 * Super admins and race admins can change results, payments, and access for others, so their
 * sessions must be verified with a second factor. Tokens without the verified-MFA claim are not
 * accepted for these roles, and their refresh tokens are not renewed.
 */
@Component
public class AdminMfaPolicy {

    private final boolean required;

    public AdminMfaPolicy(@Value("${racetracker.mfa.required-for-admins:true}") boolean required) {
        this.required = required;
    }

    public boolean isRequired() {
        return required;
    }

    public boolean requiresMfa(User.UserRole role) {
        return required && (role == User.UserRole.SUPER_ADMIN || role == User.UserRole.RACE_ADMIN);
    }

    public boolean requiresMfa(Collection<? extends GrantedAuthority> authorities) {
        if (!required || authorities == null) {
            return false;
        }
        return authorities.stream().map(GrantedAuthority::getAuthority)
            .anyMatch(authority -> "ROLE_SUPER_ADMIN".equals(authority) || "ROLE_RACE_ADMIN".equals(authority));
    }
}
