package com.racetracker.controller;

import com.racetracker.dto.notification.NotificationTemplateDto;
import com.racetracker.dto.notification.NotificationTemplateRequest;
import com.racetracker.service.NotificationTemplateService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v${racetracker.api.version}/admin/notification-templates")
public class NotificationTemplateController {

    private final NotificationTemplateService templateService;

    public NotificationTemplateController(NotificationTemplateService templateService) {
        this.templateService = templateService;
    }

    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<List<NotificationTemplateDto>> list() {
        return ResponseEntity.ok(templateService.list());
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<NotificationTemplateDto> create(@Valid @RequestBody NotificationTemplateRequest request) {
        return ResponseEntity.ok(templateService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<NotificationTemplateDto> update(
        @PathVariable UUID id,
        @Valid @RequestBody NotificationTemplateRequest request
    ) {
        return ResponseEntity.ok(templateService.update(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        templateService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
