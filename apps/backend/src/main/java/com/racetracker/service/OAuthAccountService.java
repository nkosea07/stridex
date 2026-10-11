package com.racetracker.service;

import com.racetracker.dto.auth.AuthResponse;

public interface OAuthAccountService {

    AuthResponse authenticateWithGoogle(String idToken);
}
