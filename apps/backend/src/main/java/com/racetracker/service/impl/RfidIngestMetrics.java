package com.racetracker.service.impl;

import com.racetracker.model.RfidReadEvent;
import com.racetracker.repository.RfidReadEventRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class RfidIngestMetrics {

    private final Counter duplicateCounter;
    private final Counter unknownCredentialCounter;
    private final Counter invalidReaderCounter;
    private final Counter errorCounter;
    private final Counter ignoredCounter;
    private final Counter improvedCounter;

    public RfidIngestMetrics(MeterRegistry meterRegistry, RfidReadEventRepository rfidReadEventRepository) {
        this.duplicateCounter = meterRegistry.counter("tracking.rfid.duplicate.count");
        this.unknownCredentialCounter = meterRegistry.counter("tracking.rfid.unknown_credential.count");
        this.invalidReaderCounter = meterRegistry.counter("tracking.rfid.invalid_reader.count");
        this.errorCounter = meterRegistry.counter("tracking.rfid.error.count");
        this.ignoredCounter = meterRegistry.counter("tracking.rfid.ignored.count");
        this.improvedCounter = meterRegistry.counter("tracking.rfid.earlier_read.count");
        Gauge.builder("tracking.rfid.queue.depth", rfidReadEventRepository,
                repo -> repo.countByStatus(RfidReadEvent.RfidReadEventStatus.RECEIVED))
            .description("RFID read events waiting to be normalized")
            .register(meterRegistry);
    }

    public void incrementIgnored() {
        ignoredCounter.increment();
    }

    public void incrementEarlierRead() {
        improvedCounter.increment();
    }

    public void incrementDuplicate() {
        duplicateCounter.increment();
    }

    public void incrementUnknownCredential() {
        unknownCredentialCounter.increment();
    }

    public void incrementInvalidReader() {
        invalidReaderCounter.increment();
    }

    public void incrementError() {
        errorCounter.increment();
    }
}
