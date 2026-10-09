package com.racetracker.rfidreaderdemo.reader;

import com.racetracker.rfidreaderdemo.config.DemoReaderProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "adapter.reader.mode", havingValue = "bridge", matchIfMissing = true)
public class BridgeDemoReaderSource implements ReaderSource {

    private static final Logger log = LoggerFactory.getLogger(BridgeDemoReaderSource.class);

    private final DemoReaderProperties properties;

    public BridgeDemoReaderSource(DemoReaderProperties properties) {
        this.properties = properties;
    }

    @Override
    public void start() {
        log.info(
            "Demo compact-UHF bridge mode started for {}. Post normalized reader events to /reader-events and forward them to the shared gateway.",
            properties.getReader().getName()
        );
    }

    @Override
    public void stop() {
        log.info("Demo compact-UHF bridge mode stopped for {}", properties.getReader().getName());
    }
}
