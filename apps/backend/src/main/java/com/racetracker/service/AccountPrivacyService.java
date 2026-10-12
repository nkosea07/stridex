package com.racetracker.service;

import com.racetracker.dto.privacy.ConsentDto;
import com.racetracker.dto.privacy.ConsentUpdateRequest;

import java.util.Map;
import java.util.UUID;

public interface AccountPrivacyService {

    Map<String, Object> exportData(UUID userId);

    ConsentDto getConsent(UUID userId);

    ConsentDto updateConsent(UUID userId, ConsentUpdateRequest request);

    void eraseAccount(UUID userId);
}
