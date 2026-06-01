package com.racetracker.service;

import com.racetracker.service.impl.GeoTelemetryServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class GeoTelemetryServiceTest {

    private final GeoTelemetryService geoTelemetryService = new GeoTelemetryServiceImpl();

    @Test
    void calculateDistanceKmReturnsZeroForSameCoordinate() {
        double distance = geoTelemetryService.calculateDistanceKm(
            new BigDecimal("-33.9248685"),
            new BigDecimal("18.4240553"),
            new BigDecimal("-33.9248685"),
            new BigDecimal("18.4240553")
        );

        assertThat(distance).isZero();
    }

    @Test
    void calculateDistanceKmReturnsExpectedShortDistance() {
        double distance = geoTelemetryService.calculateDistanceKm(
            new BigDecimal("-33.9248685"),
            new BigDecimal("18.4240553"),
            new BigDecimal("-33.9251924"),
            new BigDecimal("18.4237566")
        );

        assertThat(distance).isBetween(0.04, 0.05);
    }

    @Test
    void calculateDistanceKmReturnsZeroForMissingCoordinate() {
        double distance = geoTelemetryService.calculateDistanceKm(
            null,
            new BigDecimal("18.4240553"),
            new BigDecimal("-33.9251924"),
            new BigDecimal("18.4237566")
        );

        assertThat(distance).isZero();
    }

    @Test
    void isWithinMetersChecksThreshold() {
        BigDecimal latitude = new BigDecimal("-33.9248685");
        BigDecimal longitude = new BigDecimal("18.4240553");
        BigDecimal nearbyLatitude = new BigDecimal("-33.9251924");
        BigDecimal nearbyLongitude = new BigDecimal("18.4237566");

        assertThat(geoTelemetryService.isWithinMeters(latitude, longitude, nearbyLatitude, nearbyLongitude, 50)).isTrue();
        assertThat(geoTelemetryService.isWithinMeters(latitude, longitude, nearbyLatitude, nearbyLongitude, 10)).isFalse();
    }

    @Test
    void isWithinMetersReturnsFalseForMissingCoordinate() {
        assertThat(geoTelemetryService.isWithinMeters(
            null,
            new BigDecimal("18.4240553"),
            new BigDecimal("-33.9251924"),
            new BigDecimal("18.4237566"),
            50
        )).isFalse();
    }
}
