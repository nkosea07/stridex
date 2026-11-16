package com.racetracker.dto.app;

public record AppConfigDto(
    String minimumVersion,
    String latestVersion,
    String iosStoreUrl,
    String androidStoreUrl
) {
}
