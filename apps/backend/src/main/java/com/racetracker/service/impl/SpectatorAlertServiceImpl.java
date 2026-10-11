package com.racetracker.service.impl;

import com.racetracker.dto.notification.NotificationDto;
import com.racetracker.model.AthleteFollow;
import com.racetracker.model.Checkpoint;
import com.racetracker.model.Notification;
import com.racetracker.model.Participant;
import com.racetracker.repository.AthleteFollowRepository;
import com.racetracker.repository.NotificationRepository;
import com.racetracker.service.SpectatorAlertService;
import com.racetracker.websocket.TrackingLivePublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class SpectatorAlertServiceImpl implements SpectatorAlertService {

    private final AthleteFollowRepository followRepository;
    private final NotificationRepository notificationRepository;
    private final TrackingLivePublisher trackingLivePublisher;

    public SpectatorAlertServiceImpl(
        AthleteFollowRepository followRepository,
        NotificationRepository notificationRepository,
        TrackingLivePublisher trackingLivePublisher
    ) {
        this.followRepository = followRepository;
        this.notificationRepository = notificationRepository;
        this.trackingLivePublisher = trackingLivePublisher;
    }

    @Override
    @Transactional
    public void onStarted(Participant participant) {
        notifyFollowers(
            participant,
            participant.getFullName() + " started",
            bibPrefix(participant) + "started " + raceName(participant),
            Notification.NotificationType.RACE_UPDATE
        );
    }

    @Override
    @Transactional
    public void onFinished(Participant participant) {
        notifyFollowers(
            participant,
            participant.getFullName() + " finished",
            bibPrefix(participant) + "crossed the finish line",
            Notification.NotificationType.RACE_UPDATE
        );
    }

    @Override
    @Transactional
    public void onCheckpoint(Participant participant, Checkpoint checkpoint) {
        notifyFollowers(
            participant,
            participant.getFullName() + " reached " + checkpoint.getName(),
            bibPrefix(participant) + "passed " + checkpoint.getName(),
            Notification.NotificationType.CHECKPOINT_PASSED
        );
    }

    private void notifyFollowers(Participant participant, String title, String message,
                                 Notification.NotificationType type) {
        if (participant == null || participant.getRace() == null) {
            return;
        }
        List<AthleteFollow> follows = followRepository.findByRaceIdAndParticipantId(
            participant.getRace().getId(), participant.getId());
        for (AthleteFollow follow : follows) {
            if (follow.getUser() == null) {
                continue;
            }
            if (notificationRepository.existsByUserIdAndTitle(follow.getUser().getId(), title)) {
                continue;
            }
            Notification saved = notificationRepository.save(Notification.builder()
                .user(follow.getUser())
                .title(title)
                .message(message)
                .type(type)
                .build());
            trackingLivePublisher.publishUserAlert(follow.getUser().getId(), toDto(saved));
        }
    }

    private NotificationDto toDto(Notification notification) {
        NotificationDto dto = new NotificationDto();
        dto.setId(notification.getId());
        dto.setTitle(notification.getTitle());
        dto.setMessage(notification.getMessage());
        dto.setType(notification.getType() != null ? notification.getType().name().toLowerCase() : null);
        dto.setTimestamp(notification.getCreatedAt() != null ? notification.getCreatedAt() : OffsetDateTime.now());
        dto.setRead(Boolean.TRUE.equals(notification.getIsRead()));
        dto.setData(null);
        return dto;
    }

    private String bibPrefix(Participant participant) {
        return participant.getBibNumber() != null ? "Bib " + participant.getBibNumber() + " " : "";
    }

    private String raceName(Participant participant) {
        return participant.getRace() != null ? participant.getRace().getName() : "the race";
    }
}
