package com.racetracker.rfidreaderdemo.web;

import jakarta.validation.constraints.NotBlank;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public class DemoReadEventRequest {

    private String sourceEventId;

    @NotBlank
    private String rfidTag;

    private OffsetDateTime readAt;
    private String antenna;
    private BigDecimal rssi;
    private String rawPayload;

    public String getSourceEventId() {
        return sourceEventId;
    }

    public void setSourceEventId(String sourceEventId) {
        this.sourceEventId = sourceEventId;
    }

    public String getRfidTag() {
        return rfidTag;
    }

    public void setRfidTag(String rfidTag) {
        this.rfidTag = rfidTag;
    }

    public OffsetDateTime getReadAt() {
        return readAt;
    }

    public void setReadAt(OffsetDateTime readAt) {
        this.readAt = readAt;
    }

    public String getAntenna() {
        return antenna;
    }

    public void setAntenna(String antenna) {
        this.antenna = antenna;
    }

    public BigDecimal getRssi() {
        return rssi;
    }

    public void setRssi(BigDecimal rssi) {
        this.rssi = rssi;
    }

    public String getRawPayload() {
        return rawPayload;
    }

    public void setRawPayload(String rawPayload) {
        this.rawPayload = rawPayload;
    }
}
