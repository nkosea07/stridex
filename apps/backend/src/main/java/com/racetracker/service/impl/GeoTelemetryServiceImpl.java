package com.racetracker.service.impl;

import com.racetracker.service.GeoTelemetryService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class GeoTelemetryServiceImpl implements GeoTelemetryService {

    private static final double EARTH_RADIUS_KM = 6371.0;

    @Override
    public double calculateDistanceKm(
        BigDecimal latitude1,
        BigDecimal longitude1,
        BigDecimal latitude2,
        BigDecimal longitude2
    ) {
        if (hasMissingCoordinate(latitude1, longitude1, latitude2, longitude2)) {
            return 0.0;
        }

        double lat1 = latitude1.doubleValue();
        double lon1 = longitude1.doubleValue();
        double lat2 = latitude2.doubleValue();
        double lon2 = longitude2.doubleValue();
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
            + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
            * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return EARTH_RADIUS_KM * c;
    }

    @Override
    public boolean isWithinMeters(
        BigDecimal latitude1,
        BigDecimal longitude1,
        BigDecimal latitude2,
        BigDecimal longitude2,
        int meters
    ) {
        if (meters < 0 || hasMissingCoordinate(latitude1, longitude1, latitude2, longitude2)) {
            return false;
        }
        return calculateDistanceKm(latitude1, longitude1, latitude2, longitude2) * 1000 <= meters;
    }

    private boolean hasMissingCoordinate(BigDecimal... coordinates) {
        for (BigDecimal coordinate : coordinates) {
            if (coordinate == null) {
                return true;
            }
        }
        return false;
    }
}
