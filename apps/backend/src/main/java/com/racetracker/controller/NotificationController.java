package com.racetracker.controller;

import com.racetracker.dto.notification.NotificationDto;
import com.racetracker.service.NotificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v${racetracker.api.version}/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<NotificationDto>> list() {
        return ResponseEntity.ok(notificationService.getMyNotifications());
    }

    @PutMapping("/{id}/read")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<NotificationDto> markRead(@PathVariable UUID id) {
        return ResponseEntity.ok(notificationService.markRead(id));
    }

    @PostMapping("/devices")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> registerDevice(@Valid @RequestBody DeviceRegistrationRequest request) {
        notificationService.registerDevice(request.getToken(), request.getPlatform());
        return ResponseEntity.ok(Map.of("status", "registered"));
    }

    @DeleteMapping("/devices")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> unregisterDevice(@RequestParam String token) {
        notificationService.unregisterDevice(token);
        return ResponseEntity.noContent().build();
    }

    public static class DeviceRegistrationRequest {
        @NotBlank
        private String token;
        private String platform;

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public String getPlatform() {
            return platform;
        }

        public void setPlatform(String platform) {
            this.platform = platform;
        }
    }
}
