package com.racetracker.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.racetracker.repository.NotificationDeliveryRepository;
import com.racetracker.service.WhatsAppWebhookService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WhatsAppWebhookServiceImpl implements WhatsAppWebhookService {

    private final NotificationDeliveryRepository deliveryRepository;
    private final ObjectMapper objectMapper;

    public WhatsAppWebhookServiceImpl(NotificationDeliveryRepository deliveryRepository, ObjectMapper objectMapper) {
        this.deliveryRepository = deliveryRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void updateDeliveryStatuses(String rawBody) {
        try {
            JsonNode root = objectMapper.readTree(rawBody == null ? "{}" : rawBody);
            for (JsonNode entry : root.path("entry")) {
                for (JsonNode change : entry.path("changes")) {
                    for (JsonNode status : change.path("value").path("statuses")) {
                        String messageId = status.path("id").asText(null);
                        if (messageId == null) {
                            continue;
                        }
                        deliveryRepository.findFirstByProviderMessageId(messageId).ifPresent(delivery -> {
                            delivery.setProviderStatus(status.path("status").asText("unknown"));
                            deliveryRepository.save(delivery);
                        });
                    }
                }
            }
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid WhatsApp webhook payload", ex);
        }
    }
}
