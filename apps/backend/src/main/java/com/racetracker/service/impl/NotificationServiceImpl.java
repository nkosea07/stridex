package com.racetracker.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.racetracker.dto.notification.NotificationDto;
import com.racetracker.dto.notification.NotificationPreferenceDto;
import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.exception.UnauthorizedException;
import com.racetracker.model.Notification;
import com.racetracker.model.PushDevice;
import com.racetracker.model.User;
import com.racetracker.notification.NotificationMessage;
import com.racetracker.repository.NotificationRepository;
import com.racetracker.repository.PushDeviceRepository;
import com.racetracker.repository.UserRepository;
import com.racetracker.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final PushDeviceRepository pushDeviceRepository;
    private final NotificationDispatcher notificationDispatcher;
    private final ObjectMapper objectMapper;
    private final com.racetracker.service.NotificationPreferenceService preferenceService;
    private final com.racetracker.service.NotificationTemplateService notificationTemplateService;

    public NotificationServiceImpl(
        NotificationRepository notificationRepository,
        UserRepository userRepository,
        PushDeviceRepository pushDeviceRepository,
        NotificationDispatcher notificationDispatcher,
        ObjectMapper objectMapper,
        com.racetracker.service.NotificationPreferenceService preferenceService,
        com.racetracker.service.NotificationTemplateService notificationTemplateService
    ) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.pushDeviceRepository = pushDeviceRepository;
        this.notificationDispatcher = notificationDispatcher;
        this.objectMapper = objectMapper;
        this.preferenceService = preferenceService;
        this.notificationTemplateService = notificationTemplateService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotificationDto> getMyNotifications() {
        User user = resolveCurrentUser();
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
            .stream()
            .map(this::toDto)
            .toList();
    }

    @Override
    @Transactional
    public NotificationDto markRead(UUID id) {
        User user = resolveCurrentUser();
        Notification notification = notificationRepository.findByIdAndUserId(id, user.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));

        if (!Boolean.TRUE.equals(notification.getIsRead())) {
            notification.setIsRead(true);
            notification.setReadAt(OffsetDateTime.now());
            notification = notificationRepository.save(notification);
        }

        return toDto(notification);
    }

    @Override
    @Transactional
    public NotificationDto notify(UUID userId, Notification.NotificationType type, String title, String message,
                                  Map<String, String> data) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return null;
        }

        Notification notification = new Notification();
        notification.setUser(user);
        notification.setType(type == null ? Notification.NotificationType.ALERT : type);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setIsRead(false);
        notification.setData(serialize(data));
        Notification saved = notificationRepository.save(notification);

        Map<String, String> payload = data == null ? Map.of() : data;
        notificationDispatcher.dispatch(saved.getId(), new NotificationMessage(
            userId, user.getEmail(), user.getPhone(), saved.getType(), title, message, payload));

        return toDto(saved);
    }

    @Override
    @Transactional
    public NotificationDto notifyTemplated(UUID userId, Notification.NotificationType type, String templateCode,
                                           String fallbackTitle, String fallbackMessage, Map<String, String> data) {
        String title = fallbackTitle;
        String message = fallbackMessage;
        Map<String, String> payload = data == null ? new java.util.LinkedHashMap<>() : new java.util.LinkedHashMap<>(data);
        try {
            var resolved = notificationTemplateService.resolve(templateCode, null);
            if (resolved.isPresent()) {
                title = resolved.get().subject();
                message = resolved.get().body();
                if (resolved.get().metaTemplateName() != null && !resolved.get().metaTemplateName().isBlank()) {
                    payload.put("whatsappTemplate", resolved.get().metaTemplateName());
                }
                if (resolved.get().languageCode() != null && !resolved.get().languageCode().isBlank()) {
                    payload.put("whatsappLanguage", resolved.get().languageCode());
                }
            }
        } catch (RuntimeException ex) {
            log.warn("Failed to resolve notification template {}: {}", templateCode, ex.getMessage());
        }
        return notify(userId, type, title, message, payload);
    }

    @Override
    @Transactional
    public void registerDevice(String token, String platform) {        if (token == null || token.isBlank()) {
            throw new com.racetracker.exception.ApiException("Device token is required");
        }
        User user = resolveCurrentUser();
        PushDevice device = pushDeviceRepository.findByToken(token).orElseGet(PushDevice::new);
        device.setUser(user);
        device.setToken(token);
        device.setPlatform(platform);
        device.setLastSeenAt(OffsetDateTime.now());
        pushDeviceRepository.save(device);
    }

    @Override
    @Transactional
    public void unregisterDevice(String token) {
        resolveCurrentUser();
        if (token != null && !token.isBlank()) {
            pushDeviceRepository.deleteByToken(token);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationPreferenceDto getPreferences() {
        return preferenceService.get(resolveCurrentUser().getId());
    }

    @Override
    @Transactional
    public NotificationPreferenceDto updatePreferences(NotificationPreferenceDto preferences) {
        return preferenceService.update(resolveCurrentUser().getId(), preferences);
    }

    private String serialize(Map<String, String> data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception ex) {
            log.warn("Failed to serialize notification data: {}", ex.getMessage());
            return null;
        }
    }

    private Object deserialize(String data) {
        if (data == null || data.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(data, new TypeReference<Map<String, String>>() { });
        } catch (Exception ex) {
            log.warn("Failed to deserialize notification data: {}", ex.getMessage());
            return null;
        }
    }

    private NotificationDto toDto(Notification entity) {
        NotificationDto dto = new NotificationDto();
        dto.setId(entity.getId());
        dto.setTitle(entity.getTitle());
        dto.setMessage(entity.getMessage());
        dto.setType(entity.getType() != null ? entity.getType().name().toLowerCase() : null);
        dto.setTimestamp(entity.getCreatedAt());
        dto.setRead(Boolean.TRUE.equals(entity.getIsRead()));
        dto.setData(deserialize(entity.getData()));
        return dto;
    }

    private User resolveCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new UnauthorizedException("Unauthorized");
        }

        return userRepository.findByEmailIgnoreCase(authentication.getName())
            .orElseThrow(() -> new UnauthorizedException("Unauthorized"));
    }
}
