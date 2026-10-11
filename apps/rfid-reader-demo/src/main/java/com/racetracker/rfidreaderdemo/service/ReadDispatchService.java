package com.racetracker.rfidreaderdemo.service;

import com.racetracker.rfidreaderdemo.config.DemoReaderProperties;
import com.racetracker.rfidreaderdemo.gateway.GatewayClient;
import com.racetracker.rfidreaderdemo.model.NormalizedRead;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

@Service
public class ReadDispatchService {

    private static final Logger log = LoggerFactory.getLogger(ReadDispatchService.class);

    private final ConcurrentLinkedDeque<NormalizedRead> queue = new ConcurrentLinkedDeque<>();
    private final GatewayClient gatewayClient;
    private final DemoReaderProperties properties;

    public ReadDispatchService(GatewayClient gatewayClient, DemoReaderProperties properties) {
        this.gatewayClient = gatewayClient;
        this.properties = properties;
    }

    public void accept(NormalizedRead read) {
        queue.addLast(read);
    }

    public int pendingCount() {
        return queue.size();
    }

    @Scheduled(fixedDelayString = "${adapter.gateway.flush-interval-ms:500}")
    public synchronized void flush() {
        if (queue.isEmpty()) {
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
            gatewayClient.submitReads(batch);
        } catch (RuntimeException error) {
            for (int index = batch.size() - 1; index >= 0; index--) {
                queue.addFirst(batch.get(index));
            }
            log.warn("Failed to forward {} demo reads to the local gateway: {}", batch.size(), error.getMessage());
        }
    }
}
