package com.racetracker.service;

import com.racetracker.model.Notification;
import com.racetracker.model.Participant;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.RaceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Tells athletes with an account when their race's results are published and when they become official. */
@Slf4j
@Component
public class ResultNotifier {

    private final ParticipantRepository participantRepository;
    private final RaceRepository raceRepository;
    private final NotificationService notificationService;

    public ResultNotifier(ParticipantRepository participantRepository, RaceRepository raceRepository,
                          NotificationService notificationService) {
        this.participantRepository = participantRepository;
        this.raceRepository = raceRepository;
        this.notificationService = notificationService;
    }

    @Async
    @Transactional(readOnly = true)
    public void resultsPublished(UUID raceId, int version, boolean official) {
        if (!official && version != 1) {
            return;
        }
        String raceName = raceRepository.findById(raceId).map(race -> race.getName()).orElse("");
        String templateCode = official ? "RESULTS_OFFICIAL" : "RESULTS_PROVISIONAL";
        List<Participant> participants = participantRepository.findByRaceId(raceId);
        int sent = 0;
        for (Participant participant : participants) {
            if (participant.getUser() == null || !CheckpointProgression.isTimeableEntrant(participant.getStatus())) {
                continue;
            }
            try {
                notificationService.notifyLocalized(participant.getUser().getId(), Notification.NotificationType.RACE_UPDATE,
                    templateCode, new Object[] {raceName}, Map.of("kind", "RESULTS", "raceId", raceId.toString(),
                        "participantId", participant.getId().toString(), "resultsVersion", String.valueOf(version)));
                sent++;
            } catch (RuntimeException ex) {
                log.warn("Result notification failed for participant {}", participant.getId());
            }
        }
        log.info("Result notifications sent for race {} version {} official={}: {}", raceId, version, official, sent);
    }
}
