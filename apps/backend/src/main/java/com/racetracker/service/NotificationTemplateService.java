package com.racetracker.service;

import com.racetracker.dto.notification.NotificationTemplateDto;
import com.racetracker.dto.notification.NotificationTemplateRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationTemplateService {

    List<NotificationTemplateDto> list();

    NotificationTemplateDto create(NotificationTemplateRequest request);

    NotificationTemplateDto update(UUID id, NotificationTemplateRequest request);

    void delete(UUID id);

    Optional<ResolvedTemplate> resolve(String code, String locale);

    record ResolvedTemplate(String subject, String body) {
    }
}
