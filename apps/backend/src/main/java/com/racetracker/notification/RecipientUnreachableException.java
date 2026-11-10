package com.racetracker.notification;

/**
 * Thrown by a channel when the recipient has no address for it (no registered device, email,
 * or phone). The attempt is recorded as skipped: it is neither a delivery nor a provider failure.
 */
public class RecipientUnreachableException extends RuntimeException {

    public RecipientUnreachableException(String reason) {
        super(reason);
    }
}
