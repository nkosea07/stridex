package com.racetracker.controller;

import com.racetracker.dto.auth.AuthResponse;
import com.racetracker.dto.auth.OAuthLoginRequest;
import com.racetracker.exception.ApiException;
import com.racetracker.service.OAuthAccountService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class OAuthController {

    private final OAuthAccountService oAuthAccountService;

    public OAuthController(OAuthAccountService oAuthAccountService) {
        this.oAuthAccountService = oAuthAccountService;
    }

    @PostMapping("/v${racetracker.api.version}/auth/oauth/google")
    public ResponseEntity<AuthResponse> google(@Valid @RequestBody OAuthLoginRequest request) {
        if (request.getProvider() != null && !"google".equalsIgnoreCase(request.getProvider())) {
            throw new ApiException("Unsupported OAuth provider: " + request.getProvider());
        }
        return ResponseEntity.ok(oAuthAccountService.authenticateWithGoogle(request.getIdToken()));
    }
}
