package com.racetracker.service.impl;

import com.racetracker.dto.notification.NotificationTemplateDto;
import com.racetracker.dto.notification.NotificationTemplateRequest;
import com.racetracker.exception.ConflictException;
import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.model.NotificationTemplate;
import com.racetracker.repository.NotificationTemplateRepository;
import com.racetracker.service.NotificationTemplateService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class NotificationTemplateServiceImpl implements NotificationTemplateService {

    private final NotificationTemplateRepository repository;

    public NotificationTemplateServiceImpl(NotificationTemplateRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationTemplateDto> list() {
        return repository.findAllByOrderByCodeAscLocaleAsc().stream().map(this::toDto).toList();
    }

    @Override
    @Transactional
    public NotificationTemplateDto create(NotificationTemplateRequest request) {
        String code = request.getCode().trim();
        String locale = normalizeLocale(request.getLocale());
        if (repository.existsByCodeAndLocale(code, locale)) {
            throw new ConflictException("A template already exists for " + code + " (" + locale + ")");
        }
        NotificationTemplate template = NotificationTemplate.builder()
            .code(code)
            .channel(parseChannel(request.getChannel()))
            .locale(locale)
            .subject(request.getSubject().trim())
            .body(request.getBody())
            .enabled(request.getEnabled() == null || request.getEnabled())
            .build();
        return toDto(repository.save(template));
    }

    @Override
    @Transactional
    public NotificationTemplateDto update(UUID id, NotificationTemplateRequest request) {
        NotificationTemplate template = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Notification template not found"));
        template.setSubject(request.getSubject().trim());
        template.setBody(request.getBody());
        if (request.getChannel() != null) {
            template.setChannel(parseChannel(request.getChannel()));
        }
        if (request.getEnabled() != null) {
            template.setEnabled(request.getEnabled());
        }
        if (request.getMetaTemplateName() != null) {
            template.setMetaTemplateName(request.getMetaTemplateName());
        }
        if (request.getLanguageCode() != null) {
            template.setLanguageCode(request.getLanguageCode());
        }
        return toDto(repository.save(template));
    }

    @Override
    @Transactional
    public void delete(UUID id) {
        NotificationTemplate template = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Notification template not found"));
        repository.delete(template);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ResolvedTemplate> resolve(String code, String locale) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return repository.findByCodeAndLocaleAndEnabledTrue(code, normalizeLocale(locale))
            .map(template -> new ResolvedTemplate(template.getSubject(), template.getBody(),
                template.getMetaTemplateName(), template.getLanguageCode()));
    }

    private String normalizeLocale(String locale) {
        return locale == null || locale.isBlank() ? "en" : locale.trim().toLowerCase(Locale.ROOT);
    }

    private NotificationTemplate.Channel parseChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            return NotificationTemplate.Channel.IN_APP;
        }
        return NotificationTemplate.Channel.valueOf(channel.trim().toUpperCase(Locale.ROOT));
    }

    private NotificationTemplateDto toDto(NotificationTemplate template) {
        NotificationTemplateDto dto = new NotificationTemplateDto();
        dto.setId(template.getId());
        dto.setCode(template.getCode());
        dto.setChannel(template.getChannel() != null ? template.getChannel().name() : null);
        dto.setLocale(template.getLocale());
        dto.setSubject(template.getSubject());
        dto.setBody(template.getBody());
        dto.setEnabled(template.getEnabled());
        dto.setMetaTemplateName(template.getMetaTemplateName());
        dto.setLanguageCode(template.getLanguageCode());
        dto.setUpdatedAt(template.getUpdatedAt());
        return dto;
    }
}
