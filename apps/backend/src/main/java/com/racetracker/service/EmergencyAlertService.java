package com.racetracker.service;

import com.racetracker.dto.emergency.EmergencyAlertDto;
import com.racetracker.dto.emergency.EmergencyAlertRequest;

import java.util.List;
import java.util.UUID;

public interface EmergencyAlertService {

    EmergencyAlertDto raise(UUID raceId, EmergencyAlertRequest request, String actorEmail);

    EmergencyAlertDto raiseForAthlete(UUID raceId, EmergencyAlertRequest request, String actorEmail);

    List<EmergencyAlertDto> list(UUID raceId);

    EmergencyAlertDto updateStatus(UUID raceId, UUID alertId, String status, String actorEmail);
}
