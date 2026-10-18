package com.racetracker.controller;

import com.racetracker.config.RaceTrackerProperties;
import com.racetracker.dto.auth.AuthResponse;
import com.racetracker.dto.auth.OAuthLoginRequest;
import com.racetracker.exception.ApiException;
import com.racetracker.security.ClientAddressResolver;
import com.racetracker.security.RequestRateLimiter;
import com.racetracker.service.OAuthAccountService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class OAuthController {

    private static final String OAUTH_DISABLED_MESSAGE = "OAuth sign-in is unavailable";

    private final OAuthAccountService oAuthAccountService;
    private final RequestRateLimiter requestRateLimiter;
    private final ClientAddressResolver clientAddressResolver;
    private final RaceTrackerProperties raceTrackerProperties;

    public OAuthController(OAuthAccountService oAuthAccountService,
                           RequestRateLimiter requestRateLimiter,
                           ClientAddressResolver clientAddressResolver,
                           RaceTrackerProperties raceTrackerProperties) {
        this.oAuthAccountService = oAuthAccountService;
        this.requestRateLimiter = requestRateLimiter;
        this.clientAddressResolver = clientAddressResolver;
        this.raceTrackerProperties = raceTrackerProperties;
    }

    @PostMapping("/v${racetracker.api.version}/auth/oauth/google")
    public ResponseEntity<AuthResponse> google(@Valid @RequestBody OAuthLoginRequest request,
                                               HttpServletRequest servletRequest) {
        if (raceTrackerProperties.getOauth() == null || !raceTrackerProperties.getOauth().isEnabled()) {
            throw new ApiException(OAUTH_DISABLED_MESSAGE);
        }
        requestRateLimiter.checkOAuth(clientAddressResolver.resolve(servletRequest));
        if (request.getProvider() != null && !"google".equalsIgnoreCase(request.getProvider())) {
            throw new ApiException("Unsupported OAuth provider: " + request.getProvider());
        }
        return ResponseEntity.ok(oAuthAccountService.authenticateWithGoogle(request.getIdToken()));
    }
}
