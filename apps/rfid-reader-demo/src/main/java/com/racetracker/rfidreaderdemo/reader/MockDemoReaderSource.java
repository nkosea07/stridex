package com.racetracker.rfidreaderdemo.reader;

import com.racetracker.rfidreaderdemo.config.DemoReaderProperties;
import com.racetracker.rfidreaderdemo.model.NormalizedRead;
import com.racetracker.rfidreaderdemo.service.ReadDispatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicLong;

@Component
@ConditionalOnProperty(name = "adapter.reader.mode", havingValue = "mock")
public class MockDemoReaderSource implements ReaderSource {

    private static final Logger log = LoggerFactory.getLogger(MockDemoReaderSource.class);

    private final DemoReaderProperties properties;
    private final ReadDispatchService readDispatchService;
    private final AtomicLong sequence = new AtomicLong();
    private volatile boolean running;

    public MockDemoReaderSource(DemoReaderProperties properties, ReadDispatchService readDispatchService) {
        this.properties = properties;
        this.readDispatchService = readDispatchService;
    }

    @Override
    public void start() {
        running = properties.getMock().isEnabled();
        log.info("Mock compact-UHF demo reader source started for {}", properties.getReader().getName());
    }

    @Override
    public void stop() {
        running = false;
    }

    @Scheduled(fixedDelayString = "${adapter.mock.emit-interval-ms:2500}")
    void emitMockRead() {
        if (!running) {
            return;
        }

        long current = sequence.incrementAndGet();
        readDispatchService.accept(new NormalizedRead(
            properties.getReader().getName() + "-" + current,
            properties.getMock().getTagPrefix() + "-" + current,
            OffsetDateTime.now(),
            properties.getMock().getAntenna(),
            BigDecimal.valueOf(-44.2),
            "{\"simulated\":true,\"profile\":\"demo\"}"
        ));
    }
}
