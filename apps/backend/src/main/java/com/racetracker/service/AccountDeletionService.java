package com.racetracker.service;

import com.racetracker.dto.privacy.DeleteAccountRequest;
import com.racetracker.dto.privacy.DeletionStatusDto;

import java.util.UUID;

public interface AccountDeletionService {

    DeletionStatusDto request(UUID userId, DeleteAccountRequest request);

    DeletionStatusDto cancel(UUID userId);

    void cancelWithPassword(String email, String password);

    DeletionStatusDto status(UUID userId);

    int purgeExpired();
}
