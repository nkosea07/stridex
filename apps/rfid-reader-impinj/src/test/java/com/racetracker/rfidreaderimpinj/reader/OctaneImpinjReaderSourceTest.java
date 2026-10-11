package com.racetracker.rfidreaderimpinj.reader;

import com.racetracker.rfidreaderimpinj.config.ImpinjReaderProperties;
import com.racetracker.rfidreaderimpinj.service.AdapterRuntimeStatus;
import com.racetracker.rfidreaderimpinj.service.ReadDispatchService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OctaneImpinjReaderSourceTest {

    @Test
    void octaneSourceConsumesSdkTagReportsAndQueuesNormalizedReads() throws Exception {
        ImpinjReaderProperties properties = new ImpinjReaderProperties();
        properties.getReader().setMode("octane");
        properties.getReader().setHealthCheckIntervalMs(100);
        properties.getReader().setConnectRetryInitialDelayMs(50);
        properties.getReader().setConnectRetryMaxDelayMs(100);

        AdapterRuntimeStatus runtimeStatus = new AdapterRuntimeStatus();
        ReadDispatchService dispatchService = new ReadDispatchService(reads -> {}, properties, runtimeStatus);
        OctaneImpinjReaderSource source = new OctaneImpinjReaderSource(
            properties,
            dispatchService,
            runtimeStatus,
            new ReflectiveOctaneSdkFacade()
        );

        source.start();
        Thread.sleep(200);
        source.stop();

        assertThat(dispatchService.pendingCount()).isEqualTo(1);
        assertThat(runtimeStatus.snapshot().get("readerState")).isEqualTo("STOPPED");
        assertThat(runtimeStatus.snapshot().get("acceptedReads")).isEqualTo(1L);
    }
}
