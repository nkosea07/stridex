package com.racetracker.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.racetracker.config.RaceTrackerProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PesepayClientTest {

    private final PesepayClient client = new PesepayClient(new RaceTrackerProperties(), new ObjectMapper());
    private final String key = "0123456789abcdef0123456789abcdef";

    @Test
    void encryptThenDecryptRoundTrips() throws Exception {
        String json = "{\"amountDetails\":{\"amount\":10.0,\"currencyCode\":\"USD\"}}";

        String encrypted = client.encrypt(json, key);

        assertThat(encrypted).isNotEqualTo(json);
        assertThat(client.decrypt(encrypted, key)).isEqualTo(json);
    }

    @Test
    void decryptionOfTamperedPayloadFails() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> client.decrypt("not-valid-base64!!", key))
            .isInstanceOf(Exception.class);
    }
}
