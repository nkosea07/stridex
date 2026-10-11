package com.racetracker.rfidreaderdemo.gateway;

import com.racetracker.rfidreaderdemo.config.DemoReaderProperties;
import com.racetracker.rfidreaderdemo.model.NormalizedRead;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;

@Component
public class HttpGatewayClient implements GatewayClient {

    private final RestClient restClient;
    private final DemoReaderProperties properties;

    public HttpGatewayClient(RestClient.Builder builder, DemoReaderProperties properties) {
        this.restClient = builder
            .baseUrl(properties.getGateway().getBaseUrl())
            .build();
        this.properties = properties;
    }

    @Override
    public void submitReads(List<NormalizedRead> reads) {
        if (reads == null || reads.isEmpty()) {
            return;
        }

        Map<String, Object> body = Map.of(
            "reads", reads.stream().map(NormalizedRead::toGatewayMap).toList()
        );

        restClient.post()
            .uri(properties.getGateway().getReadsPath())
            .body(body)
            .retrieve()
            .onStatus(HttpStatusCode::isError, (request, response) -> {
                throw new RestClientResponseException(
                    "Gateway rejected reads",
                    response.getStatusCode().value(),
                    response.getStatusText(),
                    response.getHeaders(),
                    response.getBody().readAllBytes(),
                    null
                );
            })
            .toBodilessEntity();
    }
}
