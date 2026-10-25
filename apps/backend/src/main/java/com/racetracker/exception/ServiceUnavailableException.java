package com.racetracker.exception;

public class ServiceUnavailableException extends RuntimeException {
    private final String code;

    public ServiceUnavailableException(String message) {
        this(message, "SERVICE_UNAVAILABLE");
    }

    public ServiceUnavailableException(String message, String code) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
