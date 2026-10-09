package com.racetracker.rfidreaderdemo.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.ArrayList;
import java.util.List;

public class DemoReadBatchRequest {

    @Valid
    @NotEmpty
    private List<DemoReadEventRequest> reads = new ArrayList<>();

    public List<DemoReadEventRequest> getReads() {
        return reads;
    }

    public void setReads(List<DemoReadEventRequest> reads) {
        this.reads = reads;
    }
}
