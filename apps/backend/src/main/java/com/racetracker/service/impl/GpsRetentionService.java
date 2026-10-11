package com.racetracker.service.impl;

import com.racetracker.config.RaceTrackerProperties;
import com.racetracker.repository.GpsLocationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * Age-based retention for the high-volume gps_locations table. Disabled unless
 * {@code racetracker.tracking.retention-days} is set above zero. Partitioning on recorded_at
 * remains a dedicated operations migration.
 */
@Slf4j
@Component
public class GpsRetentionService {

    private final GpsLocationRepository gpsLocationRepository;
    private final RaceTrackerProperties properties;

    public GpsRetentionService(GpsLocationRepository gpsLocationRepository, RaceTrackerProperties properties) {
        this.gpsLocationRepository = gpsLocationRepository;
        this.properties = properties;
    }

    @Transactional
    public int purgeExpired() {
        int retentionDays = properties.getTracking().getRetentionDays();
        if (retentionDays <= 0) {
            return 0;
        }
        OffsetDateTime cutoff = OffsetDateTime.now().minus(Duration.ofDays(retentionDays));
        return gpsLocationRepository.deleteByRecordedAtBefore(cutoff);
    }

    @Scheduled(
        fixedDelayString = "${racetracker.tracking.retention-scan-interval-millis:3600000}",
        initialDelayString = "${racetracker.tracking.retention-initial-delay-millis:60000}"
    )
    public void scheduledPurge() {
        try {
            int deleted = purgeExpired();
            if (deleted > 0) {
                log.info("GPS retention purge removed {} expired location rows", deleted);
            }
        } catch (RuntimeException ex) {
            log.error("GPS retention purge failed", ex);
        }
    }
}
