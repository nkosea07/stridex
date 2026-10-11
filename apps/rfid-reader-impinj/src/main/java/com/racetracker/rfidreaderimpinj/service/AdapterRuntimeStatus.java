package com.racetracker.rfidreaderimpinj.service;

import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class AdapterRuntimeStatus {

    private volatile String readerState = "STOPPED";
    private volatile String lastReaderError;
    private volatile OffsetDateTime startedAt;
    private volatile OffsetDateTime lastConnectedAt;
    private volatile OffsetDateTime lastDisconnectedAt;
    private volatile OffsetDateTime lastReadAt;
    private volatile OffsetDateTime lastKeepaliveAt;
    private volatile OffsetDateTime lastGatewayAttemptAt;
    private volatile OffsetDateTime lastGatewaySuccessAt;
    private volatile String lastGatewayError;
    private volatile OffsetDateTime nextGatewayRetryAt;

    private final AtomicInteger consecutiveGatewayFailures = new AtomicInteger();
    private final AtomicLong acceptedReads = new AtomicLong();
    private final AtomicLong forwardedReads = new AtomicLong();

    public void markStarting() {
        this.startedAt = OffsetDateTime.now();
        this.readerState = "STARTING";
        this.lastReaderError = null;
    }

    public void markConnecting() {
        this.readerState = "CONNECTING";
    }

    public void markConnected() {
        this.readerState = "CONNECTED";
        this.lastConnectedAt = OffsetDateTime.now();
        this.lastReaderError = null;
    }

    public void markInventorying() {
        this.readerState = "INVENTORYING";
    }

    public void markDisconnected(String reason) {
        this.readerState = "DISCONNECTED";
        this.lastDisconnectedAt = OffsetDateTime.now();
        this.lastReaderError = reason;
    }

    public void markStopped() {
        this.readerState = "STOPPED";
    }

    public void markReaderError(String message) {
        this.readerState = "DEGRADED";
        this.lastReaderError = message;
    }

    public void recordReadAccepted() {
        this.lastReadAt = OffsetDateTime.now();
        this.acceptedReads.incrementAndGet();
    }

    public void recordKeepalive() {
        this.lastKeepaliveAt = OffsetDateTime.now();
    }

    public void recordGatewayAttempt() {
        this.lastGatewayAttemptAt = OffsetDateTime.now();
    }

    public void recordGatewaySuccess(int deliveredReads) {
        this.lastGatewaySuccessAt = OffsetDateTime.now();
        this.lastGatewayError = null;
        this.nextGatewayRetryAt = null;
        this.consecutiveGatewayFailures.set(0);
        this.forwardedReads.addAndGet(deliveredReads);
    }

    public void recordGatewayFailure(String error, OffsetDateTime retryAt) {
        this.lastGatewayError = error;
        this.nextGatewayRetryAt = retryAt;
        this.consecutiveGatewayFailures.incrementAndGet();
    }

    public String getReaderState() {
        return readerState;
    }

    public OffsetDateTime getNextGatewayRetryAt() {
        return nextGatewayRetryAt;
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("readerState", readerState);
        values.put("lastReaderError", lastReaderError);
        values.put("startedAt", asString(startedAt));
        values.put("lastConnectedAt", asString(lastConnectedAt));
        values.put("lastDisconnectedAt", asString(lastDisconnectedAt));
        values.put("lastReadAt", asString(lastReadAt));
        values.put("lastKeepaliveAt", asString(lastKeepaliveAt));
        values.put("acceptedReads", acceptedReads.get());
        values.put("forwardedReads", forwardedReads.get());
        values.put("lastGatewayAttemptAt", asString(lastGatewayAttemptAt));
        values.put("lastGatewaySuccessAt", asString(lastGatewaySuccessAt));
        values.put("lastGatewayError", lastGatewayError);
        values.put("consecutiveGatewayFailures", consecutiveGatewayFailures.get());
        values.put("nextGatewayRetryAt", asString(nextGatewayRetryAt));
        return values;
    }

    private String asString(OffsetDateTime value) {
        return value != null ? value.toString() : null;
    }
}
