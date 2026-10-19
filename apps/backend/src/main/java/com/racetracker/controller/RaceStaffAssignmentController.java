package com.racetracker.controller;

import com.racetracker.dto.auth.ApiMessageResponse;
import com.racetracker.dto.staff.RaceStaffAssignmentDto;
import com.racetracker.dto.staff.RaceStaffAssignmentRequest;
import com.racetracker.dto.staff.StaffPromotionRequest;
import com.racetracker.security.RaceAccessGuard;
import com.racetracker.service.RaceStaffAssignmentService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/v${racetracker.api.version}")
public class RaceStaffAssignmentController {

    private final RaceStaffAssignmentService assignmentService;
    private final RaceAccessGuard raceAccessGuard;

    public RaceStaffAssignmentController(
        RaceStaffAssignmentService assignmentService,
        RaceAccessGuard raceAccessGuard
    ) {
        this.assignmentService = assignmentService;
        this.raceAccessGuard = raceAccessGuard;
    }

    @PostMapping("/staff/users/{userId}/promote")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiMessageResponse> promote(
        @PathVariable UUID userId,
        @Valid @RequestBody StaffPromotionRequest request,
        Authentication authentication
    ) {
        assignmentService.promote(userId, request.getRole(), authentication.getName());
        return ResponseEntity.ok(new ApiMessageResponse("Staff role promoted"));
    }

    @PostMapping("/races/{raceId}/staff")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')")
    public ResponseEntity<RaceStaffAssignmentDto> assign(
        @PathVariable UUID raceId,
        @Valid @RequestBody RaceStaffAssignmentRequest request,
        Authentication authentication
    ) {
        raceAccessGuard.assertCanManage(raceId);
        return ResponseEntity.ok(assignmentService.assign(raceId, request, authentication.getName()));
    }

    @DeleteMapping("/races/{raceId}/staff/{userId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')")
    public ResponseEntity<ApiMessageResponse> revoke(
        @PathVariable UUID raceId,
        @PathVariable UUID userId,
        Authentication authentication
    ) {
        raceAccessGuard.assertCanManage(raceId);
        assignmentService.revoke(raceId, userId, authentication.getName());
        return ResponseEntity.ok(new ApiMessageResponse("Race staff assignment revoked"));
    }
}
