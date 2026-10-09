package com.racetracker.controller;

import com.racetracker.dto.audit.AuditEventPageDto;
import com.racetracker.service.AuditEventService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v${racetracker.api.version}/admin/audit-events")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AuditEventController {

    private final AuditEventService auditEventService;

    public AuditEventController(AuditEventService auditEventService) {
        this.auditEventService = auditEventService;
    }

    @GetMapping
    public ResponseEntity<AuditEventPageDto> list(
        @RequestParam(required = false) String action,
        @RequestParam(required = false) String outcome,
        @RequestParam(required = false) UUID actorUserId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size
    ) {
        return ResponseEntity.ok(auditEventService.search(action, outcome, actorUserId, page, size));
    }
}
