package com.racetracker.service;

import com.racetracker.dto.staff.RaceStaffAssignmentDto;
import com.racetracker.dto.staff.RaceStaffAssignmentRequest;
import com.racetracker.model.StaffRole;

import java.util.UUID;

public interface RaceStaffAssignmentService {
    void promote(UUID userId, StaffRole role, String actorEmail);
    RaceStaffAssignmentDto assign(UUID raceId, RaceStaffAssignmentRequest request, String actorEmail);
    void revoke(UUID raceId, UUID userId, String actorEmail);
}
