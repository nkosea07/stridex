package com.racetracker.rfidreaderdemo.reader;

import com.racetracker.rfidreaderdemo.config.DemoReaderProperties;
import com.racetracker.rfidreaderdemo.service.ReadDispatchService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MockDemoReaderSourceTest {

    @Test
    void mockSourceEmitsReadsWhenRunning() {
        DemoReaderProperties properties = new DemoReaderProperties();
        properties.getMock().setEnabled(true);
        ReadDispatchService dispatchService = new ReadDispatchService(reads -> {}, properties);
        MockDemoReaderSource source = new MockDemoReaderSource(properties, dispatchService);

        source.start();
        source.emitMockRead();

        assertThat(dispatchService.pendingCount()).isEqualTo(1);
    }
}
