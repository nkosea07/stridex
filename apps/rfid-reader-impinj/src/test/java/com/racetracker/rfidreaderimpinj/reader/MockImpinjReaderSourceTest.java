package com.racetracker.rfidreaderimpinj.reader;

import com.racetracker.rfidreaderimpinj.config.ImpinjReaderProperties;
import com.racetracker.rfidreaderimpinj.service.AdapterRuntimeStatus;
import com.racetracker.rfidreaderimpinj.gateway.GatewayClient;
import com.racetracker.rfidreaderimpinj.service.ReadDispatchService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MockImpinjReaderSourceTest {

    @Test
    void mockSourceEmitsReadsWhenRunning() {
        ImpinjReaderProperties properties = new ImpinjReaderProperties();
        properties.getMock().setEnabled(true);
        ReadDispatchService dispatchService = new ReadDispatchService(reads -> {}, properties, new AdapterRuntimeStatus());
        MockImpinjReaderSource source = new MockImpinjReaderSource(properties, dispatchService, new AdapterRuntimeStatus());

        source.start();
        source.emitMockRead();

        assertThat(dispatchService.pendingCount()).isEqualTo(1);
    }
}
