package com.racetracker.service.impl;

import com.racetracker.config.RaceTrackerProperties;
import com.racetracker.repository.GpsLocationRepository;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GpsRetentionServiceTest {

    @Test
    void purgeIsDisabledWhenRetentionDaysIsZero() {
        GpsLocationRepository repository = mock(GpsLocationRepository.class);
        RaceTrackerProperties properties = new RaceTrackerProperties();
        properties.getTracking().setRetentionDays(0);

        GpsRetentionService service = new GpsRetentionService(repository, properties);

        assertThat(service.purgeExpired()).isZero();
        verify(repository, never()).deleteBatchRecordedBefore(any(), anyInt());
    }

    @Test
    void purgeDeletesInBoundedBatchesUntilABatchIsShort() {
        GpsLocationRepository repository = mock(GpsLocationRepository.class);
        RaceTrackerProperties properties = new RaceTrackerProperties();
        properties.getTracking().setRetentionDays(30);
        properties.getTracking().setRetentionBatchSize(100);
        when(repository.deleteBatchRecordedBefore(any(OffsetDateTime.class), eq(100))).thenReturn(100, 100, 42);

        GpsRetentionService service = new GpsRetentionService(repository, properties);

        assertThat(service.purgeExpired()).isEqualTo(242);
        verify(repository, times(3)).deleteBatchRecordedBefore(any(OffsetDateTime.class), eq(100));
    }

    @Test
    void purgeStopsAtTheRunLimitSoABacklogDrainsOverSeveralRuns() {
        GpsLocationRepository repository = mock(GpsLocationRepository.class);
        RaceTrackerProperties properties = new RaceTrackerProperties();
        properties.getTracking().setRetentionDays(90);
        properties.getTracking().setRetentionBatchSize(10);
        properties.getTracking().setRetentionMaxBatchesPerRun(3);
        when(repository.deleteBatchRecordedBefore(any(OffsetDateTime.class), eq(10))).thenReturn(10);

        GpsRetentionService service = new GpsRetentionService(repository, properties);

        assertThat(service.purgeExpired()).isEqualTo(30);
        verify(repository, times(3)).deleteBatchRecordedBefore(any(OffsetDateTime.class), eq(10));
    }
}
