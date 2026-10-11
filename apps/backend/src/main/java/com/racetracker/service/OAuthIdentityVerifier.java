package com.racetracker.service;

public interface OAuthIdentityVerifier {

    /**
     * Verifies a Google ID token and returns the identity. Throws a domain exception when the
     * token is invalid, expired, or not verified by Google.
     */
    OAuthIdentity verifyGoogle(String idToken);
}
