package com.racetracker.service.impl;

import com.racetracker.dto.notification.NotificationPreferenceDto;
import com.racetracker.model.NotificationDelivery;
import com.racetracker.model.NotificationPreference;
import com.racetracker.repository.NotificationPreferenceRepository;
import com.racetracker.service.NotificationPreferenceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class NotificationPreferenceServiceImpl implements NotificationPreferenceService {

    private final NotificationPreferenceRepository repository;

    public NotificationPreferenceServiceImpl(NotificationPreferenceRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationPreferenceDto get(UUID userId) {
        return toDto(repository.findById(userId).orElse(null));
    }

    @Override
    @Transactional
    public NotificationPreferenceDto update(UUID userId, NotificationPreferenceDto preferences) {
        NotificationPreference entity = repository.findById(userId).orElseGet(() -> {
            NotificationPreference created = new NotificationPreference();
            created.setUserId(userId);
            return created;
        });
        entity.setEmailEnabled(preferences.isEmailEnabled());
        entity.setPushEnabled(preferences.isPushEnabled());
        entity.setSmsEnabled(false);
        entity.setWhatsappEnabled(preferences.isWhatsappEnabled());
        return toDto(repository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isChannelEnabled(UUID userId, NotificationDelivery.Channel channel) {
        NotificationPreference preference = repository.findById(userId).orElse(null);
        if (preference == null) {
            return true;
        }
        return switch (channel) {
            case IN_APP -> true;
            case EMAIL -> !Boolean.FALSE.equals(preference.getEmailEnabled());
            case PUSH -> !Boolean.FALSE.equals(preference.getPushEnabled());
            case SMS -> false;
            case WHATSAPP -> !Boolean.FALSE.equals(preference.getWhatsappEnabled());
        };
    }

    private NotificationPreferenceDto toDto(NotificationPreference entity) {
        NotificationPreferenceDto dto = new NotificationPreferenceDto();
        if (entity == null) {
            return dto;
        }
        dto.setEmailEnabled(!Boolean.FALSE.equals(entity.getEmailEnabled()));
        dto.setPushEnabled(!Boolean.FALSE.equals(entity.getPushEnabled()));
        dto.setSmsEnabled(false);
        dto.setWhatsappEnabled(!Boolean.FALSE.equals(entity.getWhatsappEnabled()));
        return dto;
    }
}
