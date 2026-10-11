package com.racetracker.rfidreaderdemo.service;

import com.racetracker.rfidreaderdemo.config.DemoReaderProperties;
import com.racetracker.rfidreaderdemo.gateway.GatewayClient;
import com.racetracker.rfidreaderdemo.model.NormalizedRead;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReadDispatchServiceTest {

    private RecordingGatewayClient gatewayClient;
    private ReadDispatchService service;

    @BeforeEach
    void setUp() {
        gatewayClient = new RecordingGatewayClient();
        DemoReaderProperties properties = new DemoReaderProperties();
        properties.getGateway().setBatchSize(2);
        service = new ReadDispatchService(gatewayClient, properties);
    }

    @Test
    void flushSendsReadsInConfiguredBatchSize() {
        service.accept(read("one"));
        service.accept(read("two"));
        service.accept(read("three"));

        service.flush();

        assertThat(gatewayClient.submittedBatches).hasSize(1);
        assertThat(gatewayClient.submittedBatches.get(0)).hasSize(2);
        assertThat(service.pendingCount()).isEqualTo(1);
    }

    @Test
    void flushRequeuesReadsOnGatewayFailure() {
        gatewayClient.fail = true;
        service.accept(read("one"));

        service.flush();

        assertThat(service.pendingCount()).isEqualTo(1);
    }

    private NormalizedRead read(String id) {
        return new NormalizedRead(id, "TAG-" + id, OffsetDateTime.parse("2026-06-02T06:00:00Z"), "A1", BigDecimal.ONE, "{}");
    }

    private static class RecordingGatewayClient implements GatewayClient {
        private final List<List<NormalizedRead>> submittedBatches = new ArrayList<>();
        private boolean fail;

        @Override
        public void submitReads(List<NormalizedRead> reads) {
            if (fail) {
                throw new IllegalStateException("offline");
            }
            submittedBatches.add(List.copyOf(reads));
        }
    }
}
