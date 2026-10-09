package com.racetracker.rfidreaderimpinj.web;

import com.racetracker.rfidreaderimpinj.config.ImpinjReaderProperties;
import com.racetracker.rfidreaderimpinj.service.AdapterRuntimeStatus;
import com.racetracker.rfidreaderimpinj.service.ReadDispatchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class AdapterStatusController {

    private final ReadDispatchService readDispatchService;
    private final ImpinjReaderProperties properties;
    private final AdapterRuntimeStatus adapterRuntimeStatus;

    public AdapterStatusController(
        ReadDispatchService readDispatchService,
        ImpinjReaderProperties properties,
        AdapterRuntimeStatus adapterRuntimeStatus
    ) {
        this.readDispatchService = readDispatchService;
        this.properties = properties;
        this.adapterRuntimeStatus = adapterRuntimeStatus;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("readerName", properties.getReader().getName());
        values.put("readerMode", properties.getReader().getMode());
        values.put("readerHost", properties.getReader().getHost());
        values.put("readerPort", properties.getReader().getPort());
        values.put("enabledAntennas", properties.getReader().getEnabledAntennas());
        values.put("searchMode", properties.getReader().getSearchMode());
        values.put("session", properties.getReader().getSession());
        values.put("pendingReads", readDispatchService.pendingCount());
        values.put("gatewayBaseUrl", properties.getGateway().getBaseUrl());
        values.putAll(adapterRuntimeStatus.snapshot());
        return values;
    }
}
