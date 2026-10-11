package com.racetracker.service;

import java.math.BigDecimal;

public interface GeoTelemetryService {

    double calculateDistanceKm(BigDecimal latitude1, BigDecimal longitude1, BigDecimal latitude2, BigDecimal longitude2);

    boolean isWithinMeters(
        BigDecimal latitude1,
        BigDecimal longitude1,
        BigDecimal latitude2,
        BigDecimal longitude2,
        int meters
    );
}
