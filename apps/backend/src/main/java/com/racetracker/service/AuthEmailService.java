package com.racetracker.service;

import com.racetracker.model.User;

import java.time.OffsetDateTime;

public interface AuthEmailService {
    void sendEmailVerification(User user, String token);
    void sendPasswordReset(User user, String token);
    void sendAccountDeletionScheduled(User user, OffsetDateTime scheduledFor);
    void sendAccountDeletionCompleted(String email, String firstName);
}
