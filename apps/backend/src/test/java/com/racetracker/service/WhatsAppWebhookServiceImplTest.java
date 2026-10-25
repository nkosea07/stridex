package com.racetracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.racetracker.model.NotificationDelivery;
import com.racetracker.repository.NotificationDeliveryRepository;
import com.racetracker.service.impl.WhatsAppWebhookServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WhatsAppWebhookServiceImplTest {

    @Test
    void updatesDeliveryStatusByProviderMessageId() {
        NotificationDeliveryRepository repository = mock(NotificationDeliveryRepository.class);
        NotificationDelivery delivery = new NotificationDelivery();
        delivery.setId(UUID.randomUUID());
        when(repository.findFirstByProviderMessageId("wamid.1")).thenReturn(Optional.of(delivery));
        WhatsAppWebhookServiceImpl service = new WhatsAppWebhookServiceImpl(repository, new ObjectMapper());

        service.updateDeliveryStatuses("""
            {"entry":[{"changes":[{"value":{"statuses":[{"id":"wamid.1","status":"delivered"}]}}]}]}
            """);

        assertThat(delivery.getProviderStatus()).isEqualTo("delivered");
        verify(repository).save(delivery);
    }
}
