package com.racetracker.service;

import com.racetracker.dto.notification.NotificationDto;

import java.util.List;
import java.util.UUID;

public interface NotificationService {
    List<NotificationDto> getMyNotifications();
    NotificationDto markRead(UUID id);
}
