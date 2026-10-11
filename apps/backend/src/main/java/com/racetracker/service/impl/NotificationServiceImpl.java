package com.racetracker.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.racetracker.dto.notification.NotificationDto;
import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.exception.UnauthorizedException;
import com.racetracker.model.Notification;
import com.racetracker.model.PushDevice;
import com.racetracker.model.User;
import com.racetracker.repository.NotificationRepository;
import com.racetracker.repository.PushDeviceRepository;
import com.racetracker.repository.UserRepository;
import com.racetracker.service.NotificationService;
import com.racetracker.service.PushNotificationService;
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
    private final PushNotificationService pushNotificationService;
    private final ObjectMapper objectMapper;

    public NotificationServiceImpl(
        NotificationRepository notificationRepository,
        UserRepository userRepository,
        PushDeviceRepository pushDeviceRepository,
        PushNotificationService pushNotificationService,
        ObjectMapper objectMapper
    ) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.pushDeviceRepository = pushDeviceRepository;
        this.pushNotificationService = pushNotificationService;
        this.objectMapper = objectMapper;
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

        List<String> tokens = pushDeviceRepository.findByUserId(userId).stream()
            .map(PushDevice::getToken)
            .toList();
        pushNotificationService.sendToTokens(tokens, title, message, data);

        return toDto(saved);
    }

    @Override
    @Transactional
    public void registerDevice(String token, String platform) {
        if (token == null || token.isBlank()) {
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

    private NotificationDto toDto(Notification entity) {
        NotificationDto dto = new NotificationDto();
        dto.setId(entity.getId());
        dto.setTitle(entity.getTitle());
        dto.setMessage(entity.getMessage());
        dto.setType(entity.getType() != null ? entity.getType().name().toLowerCase() : null);
        dto.setTimestamp(entity.getCreatedAt());
        dto.setRead(Boolean.TRUE.equals(entity.getIsRead()));
        dto.setData(null);
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
