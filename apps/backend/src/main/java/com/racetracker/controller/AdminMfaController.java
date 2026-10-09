package com.racetracker.controller;

import com.racetracker.dto.auth.ApiMessageResponse;
import com.racetracker.service.impl.MfaAuthenticationServiceImpl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v${racetracker.api.version}/admin/users")
public class AdminMfaController {

    private final MfaAuthenticationServiceImpl mfaAuthenticationService;

    public AdminMfaController(MfaAuthenticationServiceImpl mfaAuthenticationService) {
        this.mfaAuthenticationService = mfaAuthenticationService;
    }

    @PostMapping("/{userId}/mfa/reset")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiMessageResponse> resetMfa(@PathVariable UUID userId, Authentication authentication) {
        mfaAuthenticationService.resetBySuperAdmin(userId, authentication.getName());
        return ResponseEntity.ok(new ApiMessageResponse("MFA reset; user must enroll again before privileged access"));
    }
}
