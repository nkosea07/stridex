package com.racetracker.rfidreaderdemo.web;

import com.racetracker.rfidreaderdemo.config.DemoReaderProperties;
import com.racetracker.rfidreaderdemo.service.ReadDispatchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class AdapterStatusController {

    private final ReadDispatchService readDispatchService;
    private final DemoReaderProperties properties;

    public AdapterStatusController(ReadDispatchService readDispatchService, DemoReaderProperties properties) {
        this.readDispatchService = readDispatchService;
        this.properties = properties;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of(
            "profile", "demo",
            "readerName", properties.getReader().getName(),
            "readerMode", properties.getReader().getMode(),
            "hardwareModel", properties.getReader().getHardwareModel(),
            "expectedAntennas", properties.getReader().getExpectedAntennas(),
            "pendingReads", readDispatchService.pendingCount(),
            "gatewayBaseUrl", properties.getGateway().getBaseUrl()
        );
    }
}
