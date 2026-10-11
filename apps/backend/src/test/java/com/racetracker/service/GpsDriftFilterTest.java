package com.racetracker.service;

import com.racetracker.config.RaceTrackerProperties;
import com.racetracker.model.GpsLocation;
import com.racetracker.service.impl.GeoTelemetryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class GpsDriftFilterTest {

    private GpsDriftFilter filter;

    @BeforeEach
    void setUp() {
        RaceTrackerProperties properties = new RaceTrackerProperties();
        properties.getTracking().setAccuracyThresholdMeters(100);
        properties.getTracking().setMaxPlausibleSpeedKmh(60);
        properties.getTracking().setSmoothingFactor(0.5);
        properties.getTracking().setMinDistanceForAccumulationMeters(5);
        filter = new GpsDriftFilter(new GeoTelemetryServiceImpl(), properties);
    }

    private GpsLocation previous(BigDecimal lat, BigDecimal lng, OffsetDateTime at) {
        GpsLocation location = new GpsLocation();
        location.setLatitude(lat);
        location.setLongitude(lng);
        location.setRecordedAt(at);
        return location;
    }

    @Test
    void firstPointIsAcceptedWithRawCoordinates() {
        OffsetDateTime now = OffsetDateTime.now();
        GpsDriftFilter.FilterResult result = filter.filter(
            null, BigDecimal.valueOf(-33.9), BigDecimal.valueOf(18.4), BigDecimal.valueOf(5), now);

        assertThat(result.filtered()).isFalse();
        assertThat(result.smoothedLatitude()).isEqualByComparingTo(BigDecimal.valueOf(-33.9));
        assertThat(result.distanceKm()).isZero();
    }

    @Test
    void lowAccuracyPointIsFiltered() {
        GpsDriftFilter.FilterResult result = filter.filter(
            null, BigDecimal.valueOf(-33.9), BigDecimal.valueOf(18.4), BigDecimal.valueOf(250), OffsetDateTime.now());

        assertThat(result.filtered()).isTrue();
        assertThat(result.reason()).isEqualTo("LOW_ACCURACY");
    }

    @Test
    void implausibleSpeedIsFiltered() {
        OffsetDateTime base = OffsetDateTime.now();
        GpsLocation previous = previous(BigDecimal.valueOf(-33.9), BigDecimal.valueOf(18.4), base);

        GpsDriftFilter.FilterResult result = filter.filter(
            previous, BigDecimal.valueOf(-33.0), BigDecimal.valueOf(18.4), BigDecimal.valueOf(5), base.plusSeconds(1));

        assertThat(result.filtered()).isTrue();
        assertThat(result.reason()).isEqualTo("IMPLAUSIBLE_SPEED");
    }

    @Test
    void nonMonotonicTimeIsFiltered() {
        OffsetDateTime base = OffsetDateTime.now();
        GpsLocation previous = previous(BigDecimal.valueOf(-33.9), BigDecimal.valueOf(18.4), base);

        GpsDriftFilter.FilterResult result = filter.filter(
            previous, BigDecimal.valueOf(-33.9001), BigDecimal.valueOf(18.4), BigDecimal.valueOf(5), base.minusSeconds(5));

        assertThat(result.filtered()).isTrue();
        assertThat(result.reason()).isEqualTo("NON_MONOTONIC_TIME");
    }

    @Test
    void smoothingAveragesWithPreviousPosition() {
        OffsetDateTime base = OffsetDateTime.now();
        GpsLocation previous = previous(BigDecimal.ZERO, BigDecimal.ZERO, base);

        GpsDriftFilter.FilterResult result = filter.filter(
            previous, BigDecimal.valueOf(0.0004), BigDecimal.ZERO, BigDecimal.valueOf(5), base.plusSeconds(5));

        assertThat(result.filtered()).isFalse();
        assertThat(result.smoothedLatitude()).isEqualByComparingTo(BigDecimal.valueOf(0.0002));
    }

    @Test
    void veryShortSegmentDoesNotAccumulateDistance() {
        OffsetDateTime base = OffsetDateTime.now();
        GpsLocation previous = previous(BigDecimal.valueOf(-33.9), BigDecimal.valueOf(18.4), base);

        GpsDriftFilter.FilterResult result = filter.filter(
            previous, BigDecimal.valueOf(-33.90001), BigDecimal.valueOf(18.4), BigDecimal.valueOf(5), base.plusSeconds(2));

        assertThat(result.filtered()).isFalse();
        assertThat(result.distanceKm()).isZero();
    }
}
