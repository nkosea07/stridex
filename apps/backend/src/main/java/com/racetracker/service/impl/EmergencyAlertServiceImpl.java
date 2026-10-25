package com.racetracker.service.impl;

import com.racetracker.dto.emergency.EmergencyAlertDto;
import com.racetracker.dto.emergency.EmergencyAlertRequest;
import com.racetracker.exception.ApiException;
import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.exception.UnauthorizedException;
import com.racetracker.model.EmergencyAlert;
import com.racetracker.model.EmergencyNotificationJob;
import com.racetracker.model.NotificationDelivery;
import com.racetracker.model.Participant;
import com.racetracker.model.Race;
import com.racetracker.model.User;
import com.racetracker.repository.EmergencyAlertRepository;
import com.racetracker.repository.EmergencyNotificationJobRepository;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.RaceStaffAssignmentRepository;
import com.racetracker.repository.RaceRepository;
import com.racetracker.repository.UserRepository;
import com.racetracker.service.EmergencyAlertService;
import com.racetracker.service.NotificationChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.time.OffsetDateTime;
import java.math.BigDecimal;

@Slf4j
@Service
public class EmergencyAlertServiceImpl implements EmergencyAlertService {

    private final EmergencyAlertRepository emergencyAlertRepository;
    private final RaceRepository raceRepository;
    private final ParticipantRepository participantRepository;
    private final UserRepository userRepository;
    private final RaceStaffAssignmentRepository staffRepository;
    private final EmergencyNotificationJobRepository jobRepository;
    private final List<NotificationChannel> channels;

    public EmergencyAlertServiceImpl(
        EmergencyAlertRepository emergencyAlertRepository,
        RaceRepository raceRepository,
        ParticipantRepository participantRepository,
        UserRepository userRepository,
        RaceStaffAssignmentRepository staffRepository,
        EmergencyNotificationJobRepository jobRepository,
        List<NotificationChannel> channels
    ) {
        this.emergencyAlertRepository = emergencyAlertRepository;
        this.raceRepository = raceRepository;
        this.participantRepository = participantRepository;
        this.userRepository = userRepository;
        this.staffRepository = staffRepository;
        this.jobRepository = jobRepository;
        this.channels = channels;
    }

    @Override
    @Transactional
    public EmergencyAlertDto raise(UUID raceId, EmergencyAlertRequest request, String actorEmail) {
        return raiseInternal(raceId, request, actorEmail, false);
    }

    @Override
    @Transactional
    public EmergencyAlertDto raiseForAthlete(UUID raceId, EmergencyAlertRequest request, String actorEmail) {
        return raiseInternal(raceId, request, actorEmail, true);
    }

    private EmergencyAlertDto raiseInternal(UUID raceId, EmergencyAlertRequest request, String actorEmail,
                                            boolean requireParticipant) {
        validateCoordinates(request.getLatitude(), request.getLongitude());
        Race race = raceRepository.findById(raceId)
            .orElseThrow(() -> new ResourceNotFoundException("Race not found"));
        User reporter = userRepository.findByEmailIgnoreCase(actorEmail)
            .orElseThrow(() -> new UnauthorizedException("Unauthorized"));
        Participant participant = participantRepository.findByRaceIdAndUserId(raceId, reporter.getId()).orElse(null);
        if (requireParticipant && participant == null) {
            throw new AccessDeniedException("You do not have an athlete registration for this race");
        }

        EmergencyAlert alert = EmergencyAlert.builder()
            .race(race)
            .participant(participant)
            .reportedBy(reporter)
            .latitude(request.getLatitude())
            .longitude(request.getLongitude())
            .message(request.getMessage())
            .status(EmergencyAlert.EmergencyStatus.ACTIVE)
            .deliveryStatus(EmergencyAlert.DeliveryStatus.QUEUED)
            .build();
        EmergencyAlert saved = emergencyAlertRepository.save(alert);

        stageStaffNotifications(race, saved);
        return toDto(saved);
    }

    @Override
    @Transactional
    public EmergencyAlertDto updateStatus(UUID raceId, UUID alertId, String status, String actorEmail) {
        EmergencyAlert.EmergencyStatus next;
        try {
            next = EmergencyAlert.EmergencyStatus.valueOf(status == null ? "" : status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ApiException("Invalid emergency alert status", "EMERGENCY_STATUS_INVALID");
        }
        if (next == EmergencyAlert.EmergencyStatus.ACTIVE) {
            throw new ApiException("Emergency alerts can only be acknowledged or resolved", "EMERGENCY_STATUS_INVALID");
        }
        EmergencyAlert alert = emergencyAlertRepository.findById(alertId)
            .orElseThrow(() -> new ResourceNotFoundException("Emergency alert not found"));
        if (!alert.getRace().getId().equals(raceId)) {
            throw new ResourceNotFoundException("Emergency alert not found");
        }
        User actor = userRepository.findByEmailIgnoreCase(actorEmail).orElse(null);
        alert.setStatus(next);
        if (alert.getAcknowledgedAt() == null) {
            alert.setAcknowledgedAt(OffsetDateTime.now());
        }
        alert.setAcknowledgedBy(actor == null ? null : actor.getId());
        if (next == EmergencyAlert.EmergencyStatus.RESOLVED) {
            jobRepository.cancelPendingForAlert(alertId);
        }
        return toDto(emergencyAlertRepository.save(alert));
    }

    @Override
    @Transactional(readOnly = true)
    public List<EmergencyAlertDto> list(UUID raceId) {
        if (!raceRepository.existsById(raceId)) {
            throw new ResourceNotFoundException("Race not found");
        }
        return emergencyAlertRepository.findByRaceIdOrderByCreatedAtDesc(raceId).stream().map(this::toDto).toList();
    }

    private void stageStaffNotifications(Race race, EmergencyAlert alert) {
        Set<User> recipients = new LinkedHashSet<>();
        if (race.getCreatedBy() != null) {
            recipients.add(race.getCreatedBy());
        }
        if (race.getAdmins() != null) {
            recipients.addAll(race.getAdmins());
        }
        recipients.addAll(staffRepository.findActiveStaff(race.getId()));
        Set<UUID> staged = new LinkedHashSet<>();
        OffsetDateTime now = OffsetDateTime.now();
        for (User recipient : recipients) {
            if (recipient == null || recipient.getId() == null || Boolean.FALSE.equals(recipient.getIsActive())
                || !staged.add(recipient.getId())) {
                continue;
            }
            stageJob(alert, recipient, NotificationDelivery.Channel.IN_APP, now);
            for (NotificationChannel channel : channels) {
                if (channel.isEnabled() && channel.channel() != NotificationDelivery.Channel.IN_APP) {
                    stageJob(alert, recipient, channel.channel(), now);
                }
            }
        }
        if (staged.isEmpty()) {
            alert.setDeliveryStatus(EmergencyAlert.DeliveryStatus.ESCALATION_REQUIRED);
            emergencyAlertRepository.save(alert);
            log.error("SOS requires escalation: alertId={} raceId={} reason=no_recipients", alert.getId(), race.getId());
        }
    }

    private void stageJob(EmergencyAlert alert, User recipient, NotificationDelivery.Channel channel, OffsetDateTime now) {
        EmergencyNotificationJob job = new EmergencyNotificationJob();
        job.setId(UUID.randomUUID());
        job.setAlert(alert);
        job.setRecipient(recipient);
        job.setChannel(channel);
        job.setStatus(EmergencyNotificationJob.Status.PENDING);
        job.setNextAttemptAt(now);
        job.setCreatedAt(now);
        jobRepository.save(job);
    }

    private void validateCoordinates(BigDecimal latitude, BigDecimal longitude) {
        if ((latitude == null) != (longitude == null)
            || latitude != null && (latitude.compareTo(BigDecimal.valueOf(-90)) < 0
                || latitude.compareTo(BigDecimal.valueOf(90)) > 0
                || longitude.compareTo(BigDecimal.valueOf(-180)) < 0
                || longitude.compareTo(BigDecimal.valueOf(180)) > 0)) {
            throw new ApiException("Invalid SOS coordinates", "EMERGENCY_INVALID_COORDINATES");
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
        dto.setDeliveryStatus(alert.getDeliveryStatus() != null ? alert.getDeliveryStatus().name() : null);
        dto.setCreatedAt(alert.getCreatedAt());
        return dto;
    }
}
