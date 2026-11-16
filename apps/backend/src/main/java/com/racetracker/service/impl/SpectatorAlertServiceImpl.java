package com.racetracker.service.impl;

import com.racetracker.model.AthleteFollow;
import com.racetracker.model.Checkpoint;
import com.racetracker.model.Notification;
import com.racetracker.model.Participant;
import com.racetracker.repository.AthleteFollowRepository;
import com.racetracker.repository.NotificationRepository;
import com.racetracker.service.LocalizedMessages;
import com.racetracker.service.NotificationService;
import com.racetracker.service.SpectatorAlertService;
import com.racetracker.service.UserLocales;
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
    private final LocalizedMessages localizedMessages;
    private final UserLocales userLocales;

    public SpectatorAlertServiceImpl(
        AthleteFollowRepository followRepository,
        NotificationRepository notificationRepository,
        NotificationService notificationService,
        LocalizedMessages localizedMessages,
        UserLocales userLocales
    ) {
        this.followRepository = followRepository;
        this.notificationRepository = notificationRepository;
        this.notificationService = notificationService;
        this.localizedMessages = localizedMessages;
        this.userLocales = userLocales;
    }

    @Override
    @Transactional
    public void onStarted(Participant participant) {
        notifyFollowers(participant, "started", raceName(participant), Notification.NotificationType.RACE_UPDATE);
    }

    @Override
    @Transactional
    public void onFinished(Participant participant) {
        notifyFollowers(participant, "finished", raceName(participant), Notification.NotificationType.RACE_UPDATE);
    }

    @Override
    @Transactional
    public void onCheckpoint(Participant participant, Checkpoint checkpoint) {
        notifyFollowers(participant, "checkpoint", checkpoint.getName(), Notification.NotificationType.CHECKPOINT_PASSED);
    }

    private void notifyFollowers(Participant participant, String event, String place, Notification.NotificationType type) {
        if (participant == null || participant.getRace() == null) {
            return;
        }
        List<AthleteFollow> follows = followRepository.findByRaceIdAndParticipantId(
            participant.getRace().getId(), participant.getId());
        Map<String, String> data = Map.of("kind", "FOLLOWED_ATHLETE", "participantId", participant.getId().toString(),
            "raceId", participant.getRace().getId().toString());
        String key = "notification.spectator." + event;
        boolean hasBib = participant.getBibNumber() != null && !participant.getBibNumber().isBlank();
        Object[] args = {participant.getFullName(), hasBib ? participant.getBibNumber() : "", place == null ? "" : place};
        for (AthleteFollow follow : follows) {
            if (follow.getUser() == null) {
                continue;
            }
            java.util.Locale locale = userLocales.of(follow.getUser());
            String title = localizedMessages.get(locale, key + ".title", args);
            if (notificationRepository.existsByUserIdAndTitle(follow.getUser().getId(), title)) {
                continue;
            }
            String message = localizedMessages.get(locale, key + (hasBib ? ".body" : ".bodyNoBib"), args);
            try {
                notificationService.notify(follow.getUser().getId(), type, title, message, data);
            } catch (RuntimeException ex) {
                log.warn("Failed to deliver spectator alert to user {}: {}", follow.getUser().getId(), ex.getMessage());
            }
        }
    }

    private String raceName(Participant participant) {
        return participant.getRace() != null && participant.getRace().getName() != null ? participant.getRace().getName() : "";
    }
}
