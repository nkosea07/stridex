package com.racetracker.service.impl;

import com.racetracker.config.RaceTrackerProperties;
import com.racetracker.repository.GpsLocationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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

    public int purgeExpired() {
        RaceTrackerProperties.Tracking tracking = properties.getTracking();
        int retentionDays = tracking.getRetentionDays();
        if (retentionDays <= 0) {
            return 0;
        }
        int batchSize = Math.max(tracking.getRetentionBatchSize(), 1);
        int maxBatches = Math.max(tracking.getRetentionMaxBatchesPerRun(), 1);
        OffsetDateTime cutoff = OffsetDateTime.now().minus(Duration.ofDays(retentionDays));
        int total = 0;
        for (int batch = 0; batch < maxBatches; batch++) {
            int deleted = gpsLocationRepository.deleteBatchRecordedBefore(cutoff, batchSize);
            total += deleted;
            if (deleted < batchSize) {
                break;
            }
        }
        return total;
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
