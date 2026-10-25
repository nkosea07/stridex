package com.racetracker.service;

import com.racetracker.dto.privacy.PrivacyDeletionJobDto;
import com.racetracker.dto.privacy.PrivacyScopeVerificationRequest;

import java.util.List;
import java.util.UUID;

public interface PrivacyObjectRemediationService {

    List<PrivacyDeletionJobDto> listBlockedJobs(String scope, int limit);

    PrivacyDeletionJobDto verifyScope(UUID jobId, PrivacyScopeVerificationRequest request, String actorEmail);
}
