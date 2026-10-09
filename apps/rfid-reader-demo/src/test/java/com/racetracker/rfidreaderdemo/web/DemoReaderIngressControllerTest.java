package com.racetracker.rfidreaderdemo.web;

import com.racetracker.rfidreaderdemo.config.DemoReaderProperties;
import com.racetracker.rfidreaderdemo.service.ReadDispatchService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DemoReaderIngressControllerTest {

    @Test
    void ingestAcceptsCompactReaderEventsAndQueuesNormalizedReads() {
        ReadDispatchService dispatchService = new ReadDispatchService(reads -> {}, new DemoReaderProperties());
        DemoReaderIngressController controller = new DemoReaderIngressController(dispatchService);

        DemoReadEventRequest read = new DemoReadEventRequest();
        read.setSourceEventId(" bridge-1 ");
        read.setRfidTag(" TAG-100 ");
        read.setAntenna(" A1 ");

        DemoReadBatchRequest request = new DemoReadBatchRequest();
        request.setReads(List.of(read));

        var response = controller.ingest(request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).containsEntry("acceptedReads", 1);
        assertThat(response.getBody()).containsEntry("pendingReads", 1);
    }
}
