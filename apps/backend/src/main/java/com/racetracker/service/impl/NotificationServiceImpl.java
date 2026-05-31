package com.racetracker.service.impl;

import com.racetracker.dto.notification.NotificationDto;
import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.exception.UnauthorizedException;
import com.racetracker.model.Notification;
import com.racetracker.model.User;
import com.racetracker.repository.NotificationRepository;
import com.racetracker.repository.UserRepository;
import com.racetracker.service.NotificationService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    public NotificationServiceImpl(NotificationRepository notificationRepository, UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
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
