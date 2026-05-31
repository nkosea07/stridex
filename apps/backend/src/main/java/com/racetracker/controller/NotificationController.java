package com.racetracker.controller;

import com.racetracker.dto.notification.NotificationDto;
import com.racetracker.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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
}
