package com.racetracker.controller;

import com.racetracker.dto.privacy.ConsentDto;
import com.racetracker.dto.privacy.ConsentUpdateRequest;
import com.racetracker.dto.privacy.DeleteAccountRequest;
import com.racetracker.dto.privacy.DeletionStatusDto;
import com.racetracker.exception.UnauthorizedException;
import com.racetracker.repository.UserRepository;
import com.racetracker.service.AccountDeletionService;
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
    private final AccountDeletionService accountDeletionService;
    private final UserRepository userRepository;

    public AccountPrivacyController(
        AccountPrivacyService accountPrivacyService,
        AccountDeletionService accountDeletionService,
        UserRepository userRepository
    ) {
        this.accountPrivacyService = accountPrivacyService;
        this.accountDeletionService = accountDeletionService;
        this.userRepository = userRepository;
    }

    @GetMapping("/data")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> exportData(Authentication authentication) {
        return ResponseEntity.ok(accountPrivacyService.exportData(currentUserId(authentication)));
    }

    @GetMapping("/consent")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ConsentDto> getConsent(Authentication authentication) {
        return ResponseEntity.ok(accountPrivacyService.getConsent(currentUserId(authentication)));
    }

    @PutMapping("/consent")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ConsentDto> updateConsent(@RequestBody ConsentUpdateRequest request,
                                                    Authentication authentication) {
        return ResponseEntity.ok(accountPrivacyService.updateConsent(currentUserId(authentication), request));
    }

    @GetMapping("/deletion")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<DeletionStatusDto> deletionStatus(Authentication authentication) {
        return ResponseEntity.ok(accountDeletionService.status(currentUserId(authentication)));
    }

    @PostMapping("/deletion/cancel")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<DeletionStatusDto> cancelDeletion(Authentication authentication) {
        return ResponseEntity.ok(accountDeletionService.cancel(currentUserId(authentication)));
    }

    @DeleteMapping("")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<DeletionStatusDto> requestDeletion(
        @RequestBody(required = false) DeleteAccountRequest request,
        Authentication authentication
    ) {
        return ResponseEntity.accepted().body(accountDeletionService.request(currentUserId(authentication), request));
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
