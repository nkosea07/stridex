package com.racetracker.service.impl;

import com.racetracker.config.RaceTrackerProperties;
import com.racetracker.repository.GpsLocationRepository;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
        verify(repository, never()).deleteByRecordedAtBefore(any());
    }

    @Test
    void purgeDeletesRowsOlderThanRetentionWindow() {
        GpsLocationRepository repository = mock(GpsLocationRepository.class);
        RaceTrackerProperties properties = new RaceTrackerProperties();
        properties.getTracking().setRetentionDays(30);
        when(repository.deleteByRecordedAtBefore(any(OffsetDateTime.class))).thenReturn(42);

        GpsRetentionService service = new GpsRetentionService(repository, properties);

        assertThat(service.purgeExpired()).isEqualTo(42);
        verify(repository).deleteByRecordedAtBefore(any(OffsetDateTime.class));
    }
}
