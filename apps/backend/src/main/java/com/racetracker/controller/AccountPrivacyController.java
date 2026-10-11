package com.racetracker.controller;

import com.racetracker.dto.privacy.ConsentDto;
import com.racetracker.dto.privacy.ConsentUpdateRequest;
import com.racetracker.exception.UnauthorizedException;
import com.racetracker.repository.UserRepository;
import com.racetracker.service.AccountPrivacyService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v${racetracker.api.version}/me")
public class AccountPrivacyController {

    private final AccountPrivacyService accountPrivacyService;
    private final UserRepository userRepository;

    public AccountPrivacyController(AccountPrivacyService accountPrivacyService, UserRepository userRepository) {
        this.accountPrivacyService = accountPrivacyService;
        this.userRepository = userRepository;
    }

    @GetMapping("/data")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> exportData(Authentication authentication) {
        return ResponseEntity.ok(accountPrivacyService.exportData(currentUserId(authentication)));
    }

    @PutMapping("/consent")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ConsentDto> updateConsent(@RequestBody ConsentUpdateRequest request,
                                                    Authentication authentication) {
        return ResponseEntity.ok(accountPrivacyService.updateConsent(currentUserId(authentication), request));
    }

    @DeleteMapping("")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> eraseAccount(Authentication authentication) {
        accountPrivacyService.eraseAccount(currentUserId(authentication));
        return ResponseEntity.noContent().build();
    }

    private UUID currentUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new UnauthorizedException("Unauthorized");
        }
        return userRepository.findByEmailIgnoreCase(authentication.getName())
            .orElseThrow(() -> new UnauthorizedException("Unauthorized"))
            .getId();
    }
}
