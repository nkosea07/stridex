package com.racetracker.service;

import com.racetracker.model.User;

public interface AuthEmailService {
    void sendEmailVerification(User user, String token);
    void sendPasswordReset(User user, String token);
}
