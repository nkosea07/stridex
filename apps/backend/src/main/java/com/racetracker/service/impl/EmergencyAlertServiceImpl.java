package com.racetracker.service.impl;

import com.racetracker.dto.emergency.EmergencyAlertDto;
import com.racetracker.dto.emergency.EmergencyAlertRequest;
import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.exception.UnauthorizedException;
import com.racetracker.model.EmergencyAlert;
import com.racetracker.model.Notification;
import com.racetracker.model.Participant;
import com.racetracker.model.Race;
import com.racetracker.model.User;
import com.racetracker.repository.EmergencyAlertRepository;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.RaceRepository;
import com.racetracker.repository.UserRepository;
import com.racetracker.service.EmergencyAlertService;
import com.racetracker.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class EmergencyAlertServiceImpl implements EmergencyAlertService {

    private final EmergencyAlertRepository emergencyAlertRepository;
    private final RaceRepository raceRepository;
    private final ParticipantRepository participantRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public EmergencyAlertServiceImpl(
        EmergencyAlertRepository emergencyAlertRepository,
        RaceRepository raceRepository,
        ParticipantRepository participantRepository,
        UserRepository userRepository,
        NotificationService notificationService
    ) {
        this.emergencyAlertRepository = emergencyAlertRepository;
        this.raceRepository = raceRepository;
        this.participantRepository = participantRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    @Override
    @Transactional
    public EmergencyAlertDto raise(UUID raceId, EmergencyAlertRequest request, String actorEmail) {
        Race race = raceRepository.findById(raceId)
            .orElseThrow(() -> new ResourceNotFoundException("Race not found"));
        User reporter = userRepository.findByEmailIgnoreCase(actorEmail)
            .orElseThrow(() -> new UnauthorizedException("Unauthorized"));
        Participant participant = participantRepository.findByRaceIdAndUserId(raceId, reporter.getId()).orElse(null);

        EmergencyAlert alert = EmergencyAlert.builder()
            .race(race)
            .participant(participant)
            .reportedBy(reporter)
            .latitude(request.getLatitude())
            .longitude(request.getLongitude())
            .message(request.getMessage())
            .status(EmergencyAlert.EmergencyStatus.ACTIVE)
            .build();
        EmergencyAlert saved = emergencyAlertRepository.save(alert);

        notifyRaceStaff(race, participant, reporter, saved);
        return toDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmergencyAlertDto> list(UUID raceId) {
        if (!raceRepository.existsById(raceId)) {
            throw new ResourceNotFoundException("Race not found");
        }
        return emergencyAlertRepository.findByRaceIdOrderByCreatedAtDesc(raceId).stream().map(this::toDto).toList();
    }

    private void notifyRaceStaff(Race race, Participant participant, User reporter, EmergencyAlert alert) {
        Set<User> recipients = new LinkedHashSet<>();
        if (race.getCreatedBy() != null) {
            recipients.add(race.getCreatedBy());
        }
        if (race.getAdmins() != null) {
            recipients.addAll(race.getAdmins());
        }
        if (recipients.isEmpty()) {
            log.warn("Emergency alert {} raised but race {} has no staff to notify", alert.getId(), race.getId());
            return;
        }

        String who = participant != null ? participant.getFullName()
            : (reporter.getFirstName() == null ? "An athlete" : reporter.getFirstName());
        String bib = participant != null && participant.getBibNumber() != null ? " (bib " + participant.getBibNumber() + ")" : "";
        String title = "EMERGENCY: " + who + bib;
        String message = alert.getMessage() == null || alert.getMessage().isBlank()
            ? "An athlete has raised an emergency alert."
            : alert.getMessage();
        Map<String, String> data = Map.of(
            "emergencyAlertId", alert.getId().toString(),
            "raceId", race.getId().toString(),
            "latitude", String.valueOf(alert.getLatitude()),
            "longitude", String.valueOf(alert.getLongitude())
        );

        for (User recipient : recipients) {
            if (recipient == null || recipient.getId() == null) {
                continue;
            }
            try {
                notificationService.notify(recipient.getId(), Notification.NotificationType.ALERT, title, message, data);
            } catch (RuntimeException ex) {
                log.warn("Failed to notify user {} about emergency alert {}: {}",
                    recipient.getId(), alert.getId(), ex.getMessage());
            }
        }
    }

    private EmergencyAlertDto toDto(EmergencyAlert alert) {
        EmergencyAlertDto dto = new EmergencyAlertDto();
        dto.setId(alert.getId());
        dto.setRaceId(alert.getRace() != null ? alert.getRace().getId() : null);
        dto.setParticipantId(alert.getParticipant() != null ? alert.getParticipant().getId() : null);
        dto.setParticipantName(alert.getParticipant() != null ? alert.getParticipant().getFullName() : null);
        dto.setLatitude(alert.getLatitude());
        dto.setLongitude(alert.getLongitude());
        dto.setMessage(alert.getMessage());
        dto.setStatus(alert.getStatus() != null ? alert.getStatus().name() : null);
        dto.setCreatedAt(alert.getCreatedAt());
        return dto;
    }
}
