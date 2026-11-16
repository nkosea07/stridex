package com.racetracker.controller;

import com.racetracker.config.RaceTrackerProperties;
import com.racetracker.dto.app.AppConfigDto;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
public class AppConfigController {

    private final RaceTrackerProperties properties;

    public AppConfigController(RaceTrackerProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/v${racetracker.api.version}/public/app-config")
    public ResponseEntity<AppConfigDto> appConfig() {
        RaceTrackerProperties.Mobile mobile = properties.getMobile();
        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
            .body(new AppConfigDto(blankToNull(mobile.getMinimumVersion()), blankToNull(mobile.getLatestVersion()),
                blankToNull(mobile.getIosStoreUrl()), blankToNull(mobile.getAndroidStoreUrl())));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
