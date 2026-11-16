package com.racetracker.service;

import com.racetracker.dto.tracking.SelfScanClaimDto;
import com.racetracker.dto.tracking.SelfScanDecisionRequest;

import java.util.List;
import java.util.UUID;

public interface SelfScanService {

    byte[] selfScanQrPng(UUID raceId, UUID checkpointId);

    void rotateSelfScanCode(UUID raceId, UUID checkpointId);

    List<SelfScanClaimDto> openClaims(UUID raceId);

    SelfScanClaimDto decide(UUID raceId, UUID claimId, SelfScanDecisionRequest request);
}
