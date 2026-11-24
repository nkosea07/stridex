package com.racetracker.service.impl;

import com.racetracker.model.Notification;
import com.racetracker.model.Participant;
import com.racetracker.model.Race;
import com.racetracker.model.RaceCategory;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.RaceRepository;
import com.racetracker.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
public class RegistrationCapacity {

    public static final Set<Participant.ParticipantStatus> OCCUPYING_STATUSES = EnumSet.of(
        Participant.ParticipantStatus.REGISTERED,
        Participant.ParticipantStatus.CONFIRMED,
        Participant.ParticipantStatus.CHECKED_IN,
        Participant.ParticipantStatus.STARTED,
        Participant.ParticipantStatus.FINISHED,
        Participant.ParticipantStatus.PENDING_REVIEW
    );

    private static final Set<Race.RaceStatus> PROMOTION_CLOSED = EnumSet.of(
        Race.RaceStatus.IN_PROGRESS, Race.RaceStatus.COMPLETED, Race.RaceStatus.CANCELLED);

    private final ParticipantRepository participantRepository;
    private final RaceRepository raceRepository;
    private final ParticipantIdentityService participantIdentityService;
    private final NotificationService notificationService;

    public RegistrationCapacity(ParticipantRepository participantRepository, RaceRepository raceRepository,
                                ParticipantIdentityService participantIdentityService,
                                NotificationService notificationService) {
        this.participantRepository = participantRepository;
        this.raceRepository = raceRepository;
        this.participantIdentityService = participantIdentityService;
        this.notificationService = notificationService;
    }

    public boolean isRaceFull(Race race) {
        if (race.getMaxParticipants() == null) {
            return false;
        }
        return participantRepository.countByRaceIdAndStatusIn(race.getId(), OCCUPYING_STATUSES) >= race.getMaxParticipants();
    }

    public boolean isCategoryFull(UUID raceId, RaceCategory category) {
        if (category == null || category.getMaxParticipants() == null) {
            return false;
        }
        return participantRepository.countByRaceIdAndCategoryIdAndStatusIn(raceId, category.getId(), OCCUPYING_STATUSES)
            >= category.getMaxParticipants();
    }

    @Transactional
    public int fillFreedPlaces(UUID raceId) {
        Race race = raceRepository.findByIdForUpdate(raceId).orElse(null);
        if (race == null || PROMOTION_CLOSED.contains(race.getStatus())) {
            return 0;
        }
        int promoted = 0;
        for (Participant waiting : participantRepository.findByRaceIdAndStatusOrderByWaitlistPositionAscRegisteredAtAsc(
            raceId, Participant.ParticipantStatus.WAITLISTED)) {
            if (isRaceFull(race)) {
                break;
            }
            if (isCategoryFull(raceId, waiting.getCategory())) {
                continue;
            }
            waiting.setStatus(Boolean.TRUE.equals(race.getRequireApproval())
                ? Participant.ParticipantStatus.PENDING_REVIEW
                : Participant.ParticipantStatus.REGISTERED);
            waiting.setWaitlistPosition(null);
            participantIdentityService.assign(waiting);
            Participant saved = participantRepository.save(waiting);
            notifyAfterCommit(saved, race);
            promoted++;
        }
        return promoted;
    }

    private void notifyAfterCommit(Participant participant, Race race) {
        if (participant.getUser() == null) {
            return;
        }
        UUID userId = participant.getUser().getId();
        Object[] args = {race.getName() == null ? "" : race.getName()};
        Map<String, String> data = Map.of("kind", "REGISTRATION", "participantId", participant.getId().toString(),
            "raceId", race.getId().toString());
        Runnable send = () -> {
            try {
                notificationService.notifyLocalized(userId, Notification.NotificationType.RACE_UPDATE,
                    "registration.place_offered", args, data);
            } catch (RuntimeException ex) {
                log.warn("Waitlist place notification failed for participant {}", participant.getId());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }
}
