package com.racetracker.service;

/**
 * A verified identity returned by an OAuth identity provider.
 */
public record OAuthIdentity(String provider, String subject, String email, boolean emailVerified,
                            String firstName, String lastName) {
}
