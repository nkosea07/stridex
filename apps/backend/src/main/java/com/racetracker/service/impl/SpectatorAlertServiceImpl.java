package com.racetracker.service.impl;

import com.racetracker.model.AthleteFollow;
import com.racetracker.model.Checkpoint;
import com.racetracker.model.Notification;
import com.racetracker.model.Participant;
import com.racetracker.repository.AthleteFollowRepository;
import com.racetracker.repository.NotificationRepository;
import com.racetracker.service.NotificationService;
import com.racetracker.service.SpectatorAlertService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class SpectatorAlertServiceImpl implements SpectatorAlertService {

    private final AthleteFollowRepository followRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;

    public SpectatorAlertServiceImpl(
        AthleteFollowRepository followRepository,
        NotificationRepository notificationRepository,
        NotificationService notificationService
    ) {
        this.followRepository = followRepository;
        this.notificationRepository = notificationRepository;
        this.notificationService = notificationService;
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
        Map<String, String> data = Map.of("participantId", participant.getId().toString());
        for (AthleteFollow follow : follows) {
            if (follow.getUser() == null) {
                continue;
            }
            if (notificationRepository.existsByUserIdAndTitle(follow.getUser().getId(), title)) {
                continue;
            }
            try {
                notificationService.notify(follow.getUser().getId(), type, title, message, data);
            } catch (RuntimeException ex) {
                log.warn("Failed to deliver spectator alert to user {}: {}", follow.getUser().getId(), ex.getMessage());
            }
        }
    }

    private String bibPrefix(Participant participant) {
        return participant.getBibNumber() != null ? "Bib " + participant.getBibNumber() + " " : "";
    }

    private String raceName(Participant participant) {
        return participant.getRace() != null ? participant.getRace().getName() : "the race";
    }
}
