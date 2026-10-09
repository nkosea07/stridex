package com.racetracker.rfidreaderimpinj.reader;

import com.racetracker.rfidreaderimpinj.config.ImpinjReaderProperties;
import com.racetracker.rfidreaderimpinj.model.NormalizedRead;
import com.racetracker.rfidreaderimpinj.service.AdapterRuntimeStatus;
import com.racetracker.rfidreaderimpinj.service.ReadDispatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicLong;

@Component
@ConditionalOnProperty(name = "adapter.reader.mode", havingValue = "mock", matchIfMissing = true)
public class MockImpinjReaderSource implements ReaderSource {

    private static final Logger log = LoggerFactory.getLogger(MockImpinjReaderSource.class);

    private final ImpinjReaderProperties properties;
    private final ReadDispatchService readDispatchService;
    private final AdapterRuntimeStatus adapterRuntimeStatus;
    private final AtomicLong sequence = new AtomicLong();
    private volatile boolean running;

    public MockImpinjReaderSource(
        ImpinjReaderProperties properties,
        ReadDispatchService readDispatchService,
        AdapterRuntimeStatus adapterRuntimeStatus
    ) {
        this.properties = properties;
        this.readDispatchService = readDispatchService;
        this.adapterRuntimeStatus = adapterRuntimeStatus;
    }

    @Override
    public void start() {
        running = properties.getMock().isEnabled();
        adapterRuntimeStatus.markStarting();
        adapterRuntimeStatus.markConnected();
        adapterRuntimeStatus.markInventorying();
        log.info("Mock Impinj reader source started for {}", properties.getReader().getName());
    }

    @Override
    public void stop() {
        running = false;
        adapterRuntimeStatus.markStopped();
    }

    @Scheduled(fixedDelayString = "${adapter.mock.emit-interval-ms:2000}")
    void emitMockRead() {
        if (!running) {
            return;
        }

        long current = sequence.incrementAndGet();
        adapterRuntimeStatus.recordKeepalive();
        readDispatchService.accept(new NormalizedRead(
            properties.getReader().getName() + "-" + current,
            properties.getMock().getTagPrefix() + "-" + current,
            OffsetDateTime.now(),
            properties.getMock().getAntenna(),
            BigDecimal.valueOf(-48.5),
            "{\"simulated\":true}"
        ));
    }
}
