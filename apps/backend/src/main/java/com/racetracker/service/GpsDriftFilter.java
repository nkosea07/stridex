package com.racetracker.service;

import com.racetracker.config.RaceTrackerProperties;
import com.racetracker.model.GpsLocation;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * Applies conservative GPS drift filtering and exponential smoothing at ingest.
 * Raw coordinates are always retained by the caller; this component only decides
 * whether a point is usable for distance accumulation and what smoothed position
 * should be persisted for display/heatmap purposes.
 */
@Component
public class GpsDriftFilter {

    private final GeoTelemetryService geoTelemetryService;
    private final RaceTrackerProperties properties;

    public GpsDriftFilter(GeoTelemetryService geoTelemetryService, RaceTrackerProperties properties) {
        this.geoTelemetryService = geoTelemetryService;
        this.properties = properties;
    }

    public FilterResult filter(GpsLocation previous, BigDecimal latitude, BigDecimal longitude,
                               BigDecimal accuracy, OffsetDateTime recordedAt) {
        if (latitude == null || longitude == null) {
            return FilterResult.rejected("INVALID_COORDINATE");
        }

        double accuracyThreshold = properties.getTracking().getAccuracyThresholdMeters();
        if (accuracy != null && accuracy.doubleValue() > accuracyThreshold) {
            return FilterResult.rejected("LOW_ACCURACY");
        }

        BigDecimal smoothedLat = latitude;
        BigDecimal smoothedLng = longitude;
        double distanceKm = 0;

        if (previous != null && recordedAt != null && previous.getRecordedAt() != null) {
            if (!recordedAt.isAfter(previous.getRecordedAt())) {
                return FilterResult.rejected("NON_MONOTONIC_TIME");
            }

            BigDecimal prevLat = effectiveLatitude(previous);
            BigDecimal prevLng = effectiveLongitude(previous);
            double segmentKm = geoTelemetryService
                .calculateDistanceKm(prevLat, prevLng, latitude, longitude);
            double seconds = Duration.between(previous.getRecordedAt(), recordedAt).toMillis() / 1000.0;
            double maxSpeed = properties.getTracking().getMaxPlausibleSpeedKmh();
            if (seconds > 0 && (segmentKm / (seconds / 3600.0)) > maxSpeed) {
                return FilterResult.rejected("IMPLAUSIBLE_SPEED");
            }

            double alpha = properties.getTracking().getSmoothingFactor();
            smoothedLat = smooth(prevLat, latitude, alpha);
            smoothedLng = smooth(prevLng, longitude, alpha);
            distanceKm = geoTelemetryService
                .calculateDistanceKm(prevLat, prevLng, smoothedLat, smoothedLng);
        }

        double minKm = properties.getTracking().getMinDistanceForAccumulationMeters() / 1000.0;
        if (distanceKm < minKm) {
            distanceKm = 0;
        }
        return FilterResult.accepted(smoothedLat, smoothedLng, distanceKm);
    }

    public static BigDecimal effectiveLatitude(GpsLocation location) {
        return location.getSmoothedLatitude() != null ? location.getSmoothedLatitude() : location.getLatitude();
    }

    public static BigDecimal effectiveLongitude(GpsLocation location) {
        return location.getSmoothedLongitude() != null ? location.getSmoothedLongitude() : location.getLongitude();
    }

    private BigDecimal smooth(BigDecimal previous, BigDecimal current, double alpha) {
        double value = alpha * current.doubleValue() + (1 - alpha) * previous.doubleValue();
        return BigDecimal.valueOf(value).setScale(8, RoundingMode.HALF_UP);
    }

    public record FilterResult(boolean filtered, String reason, BigDecimal smoothedLatitude,
                               BigDecimal smoothedLongitude, double distanceKm) {

        static FilterResult rejected(String reason) {
            return new FilterResult(true, reason, null, null, 0);
        }

        static FilterResult accepted(BigDecimal latitude, BigDecimal longitude, double distanceKm) {
            return new FilterResult(false, null, latitude, longitude, distanceKm);
        }
    }
}
