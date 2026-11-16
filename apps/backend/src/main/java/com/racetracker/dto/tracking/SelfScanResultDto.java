package com.racetracker.dto.tracking;

public record SelfScanResultDto(String status, String reason) {

    public static SelfScanResultDto verified() {
        return new SelfScanResultDto("VERIFIED", null);
    }

    public static SelfScanResultDto provisional(String reason) {
        return new SelfScanResultDto("PROVISIONAL", reason);
    }
}
