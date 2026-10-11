package com.racetracker.controller;

import com.racetracker.dto.auth.OrganizerApplicationDecisionRequest;
import com.racetracker.dto.auth.OrganizerApplicationPageDto;
import com.racetracker.dto.auth.OrganizerApplicationReviewDto;
import com.racetracker.service.OrganizerApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v${racetracker.api.version}/admin/organizer-applications")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class OrganizerApplicationAdminController {

    private final OrganizerApplicationService organizerApplicationService;

    public OrganizerApplicationAdminController(OrganizerApplicationService organizerApplicationService) {
        this.organizerApplicationService = organizerApplicationService;
    }

    @GetMapping
    public ResponseEntity<OrganizerApplicationPageDto> list(
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(organizerApplicationService.listApplications(status, page, size));
    }

    @PostMapping("/{applicationId}/decision")
    public ResponseEntity<OrganizerApplicationReviewDto> decide(
        @PathVariable UUID applicationId,
        @Valid @RequestBody OrganizerApplicationDecisionRequest request,
        Authentication authentication
    ) {
        return ResponseEntity.ok(organizerApplicationService.decide(applicationId, request, authentication.getName()));
    }
}
