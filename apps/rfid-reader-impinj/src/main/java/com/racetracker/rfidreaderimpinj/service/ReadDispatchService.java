package com.racetracker.rfidreaderimpinj.service;

import com.racetracker.rfidreaderimpinj.config.ImpinjReaderProperties;
import com.racetracker.rfidreaderimpinj.gateway.GatewayClient;
import com.racetracker.rfidreaderimpinj.model.NormalizedRead;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

@Service
public class ReadDispatchService {

    private static final Logger log = LoggerFactory.getLogger(ReadDispatchService.class);

    private final ConcurrentLinkedDeque<NormalizedRead> queue = new ConcurrentLinkedDeque<>();
    private final GatewayClient gatewayClient;
    private final ImpinjReaderProperties properties;
    private final AdapterRuntimeStatus adapterRuntimeStatus;
    private long currentRetryDelayMs;

    public ReadDispatchService(
        GatewayClient gatewayClient,
        ImpinjReaderProperties properties,
        AdapterRuntimeStatus adapterRuntimeStatus
    ) {
        this.gatewayClient = gatewayClient;
        this.properties = properties;
        this.adapterRuntimeStatus = adapterRuntimeStatus;
        this.currentRetryDelayMs = properties.getGateway().getRetryInitialDelayMs();
    }

    public void accept(NormalizedRead read) {
        queue.addLast(read);
        adapterRuntimeStatus.recordReadAccepted();
    }

    public int pendingCount() {
        return queue.size();
    }

    @Scheduled(fixedDelayString = "${adapter.gateway.flush-interval-ms:500}")
    public synchronized void flush() {
        if (queue.isEmpty()) {
            return;
        }
        if (adapterRuntimeStatus.getNextGatewayRetryAt() != null
            && OffsetDateTime.now().isBefore(adapterRuntimeStatus.getNextGatewayRetryAt())) {
            return;
        }

        List<NormalizedRead> batch = new ArrayList<>();
        while (batch.size() < properties.getGateway().getBatchSize()) {
            NormalizedRead next = queue.pollFirst();
            if (next == null) {
                break;
            }
            batch.add(next);
        }

        if (batch.isEmpty()) {
            return;
        }

        try {
            adapterRuntimeStatus.recordGatewayAttempt();
            gatewayClient.submitReads(batch);
            adapterRuntimeStatus.recordGatewaySuccess(batch.size());
            currentRetryDelayMs = properties.getGateway().getRetryInitialDelayMs();
        } catch (RuntimeException error) {
            for (int index = batch.size() - 1; index >= 0; index--) {
                queue.addFirst(batch.get(index));
            }
            OffsetDateTime retryAt = OffsetDateTime.now().plus(Duration.ofMillis(currentRetryDelayMs));
            adapterRuntimeStatus.recordGatewayFailure(error.getMessage(), retryAt);
            currentRetryDelayMs = Math.min(
                currentRetryDelayMs * 2,
                properties.getGateway().getRetryMaxDelayMs()
            );
            log.warn("Failed to forward {} reads to the local gateway: {}", batch.size(), error.getMessage());
        }
    }
}
