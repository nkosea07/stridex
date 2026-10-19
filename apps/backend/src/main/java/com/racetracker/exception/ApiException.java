package com.racetracker.exception;

public class ApiException extends RuntimeException {
    private final String code;

    public ApiException(String message) {
        this(message, "API_ERROR");
    }

    public ApiException(String message, String code) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
