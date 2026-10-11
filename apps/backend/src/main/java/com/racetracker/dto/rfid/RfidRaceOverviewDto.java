package com.racetracker.dto.rfid;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RfidRaceOverviewDto {
    private int totalReaders;
    private int activeReaders;
    private int onlineReaders;
    private int degradedReaders;
    private int offlineReaders;
    private long totalEvents;
    private long processedEvents;
    private long queueDepth;
    private long duplicateCount;
    private double duplicateRate;
    private long unknownCredentialCount;
    private double unknownCredentialRate;
    private long invalidReaderCount;
    private long errorCount;
    private Long averageIngressLagMillis;
    private Long oldestQueuedReadAgeSeconds;
    private int readerSilenceTimeoutSeconds;
    private int gatewayBacklogDegradedThreshold;
}
