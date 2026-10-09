package com.racetracker.rfidreaderimpinj.service;

import com.racetracker.rfidreaderimpinj.config.ImpinjReaderProperties;
import com.racetracker.rfidreaderimpinj.gateway.GatewayClient;
import com.racetracker.rfidreaderimpinj.model.NormalizedRead;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReadDispatchServiceTest {

    private RecordingGatewayClient gatewayClient;
    private ReadDispatchService service;
    private AdapterRuntimeStatus adapterRuntimeStatus;

    @BeforeEach
    void setUp() {
        gatewayClient = new RecordingGatewayClient();
        ImpinjReaderProperties properties = new ImpinjReaderProperties();
        properties.getGateway().setBatchSize(2);
        properties.getGateway().setRetryInitialDelayMs(1000);
        properties.getGateway().setRetryMaxDelayMs(4000);
        adapterRuntimeStatus = new AdapterRuntimeStatus();
        service = new ReadDispatchService(gatewayClient, properties, adapterRuntimeStatus);
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
        assertThat(adapterRuntimeStatus.snapshot().get("lastGatewayError")).isEqualTo("offline");
    }

    @Test
    void flushSkipsImmediateRetryWhileBackoffWindowIsActive() {
        gatewayClient.fail = true;
        service.accept(read("one"));

        service.flush();
        service.flush();

        assertThat(gatewayClient.attempts).isEqualTo(1);
    }

    private NormalizedRead read(String id) {
        return new NormalizedRead(id, "TAG-" + id, OffsetDateTime.parse("2026-06-02T06:00:00Z"), "A1", BigDecimal.ONE, "{}");
    }

    private static class RecordingGatewayClient implements GatewayClient {
        private final List<List<NormalizedRead>> submittedBatches = new ArrayList<>();
        private boolean fail;
        private int attempts;

        @Override
        public void submitReads(List<NormalizedRead> reads) {
            attempts++;
            if (fail) {
                throw new IllegalStateException("offline");
            }
            submittedBatches.add(List.copyOf(reads));
        }
    }
}
