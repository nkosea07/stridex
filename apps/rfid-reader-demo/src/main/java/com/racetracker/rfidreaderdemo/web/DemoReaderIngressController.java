package com.racetracker.rfidreaderdemo.web;

import com.racetracker.rfidreaderdemo.model.NormalizedRead;
import com.racetracker.rfidreaderdemo.service.ReadDispatchService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class DemoReaderIngressController {

    private final ReadDispatchService readDispatchService;

    public DemoReaderIngressController(ReadDispatchService readDispatchService) {
        this.readDispatchService = readDispatchService;
    }

    @PostMapping("/reader-events")
    public ResponseEntity<Map<String, Object>> ingest(@Valid @RequestBody DemoReadBatchRequest request) {
        for (DemoReadEventRequest read : request.getReads()) {
            readDispatchService.accept(new NormalizedRead(
                trimToNull(read.getSourceEventId()),
                read.getRfidTag().trim(),
                read.getReadAt() != null ? read.getReadAt() : OffsetDateTime.now(),
                trimToNull(read.getAntenna()),
                read.getRssi(),
                trimToNull(read.getRawPayload())
            ));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("acceptedReads", request.getReads().size());
        response.put("pendingReads", readDispatchService.pendingCount());
        return ResponseEntity.accepted().body(response);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
