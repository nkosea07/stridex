package com.racetracker.service;

import com.racetracker.dto.participant.PenaltyDto;
import com.racetracker.dto.participant.PenaltyRequest;

import java.util.List;
import java.util.UUID;

public interface PenaltyService {

    List<PenaltyDto> list(UUID raceId, UUID participantId);

    PenaltyDto add(UUID raceId, UUID participantId, PenaltyRequest request, String actorEmail);

    void remove(UUID raceId, UUID penaltyId);
}
