package com.racetracker.service.impl;

import com.racetracker.dto.participant.PenaltyDto;
import com.racetracker.dto.participant.PenaltyRequest;
import com.racetracker.exception.ApiException;
import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.model.Participant;
import com.racetracker.model.ParticipantPenalty;
import com.racetracker.repository.ParticipantPenaltyRepository;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.UserRepository;
import com.racetracker.service.PenaltyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class PenaltyServiceImpl implements PenaltyService {

    private final ParticipantPenaltyRepository penaltyRepository;
    private final ParticipantRepository participantRepository;
    private final UserRepository userRepository;

    private final com.racetracker.service.ResultLock resultLock;

    public PenaltyServiceImpl(
        ParticipantPenaltyRepository penaltyRepository,
        ParticipantRepository participantRepository,
        UserRepository userRepository,
        com.racetracker.service.ResultLock resultLock
    ) {
        this.penaltyRepository = penaltyRepository;
        this.participantRepository = participantRepository;
        this.userRepository = userRepository;
        this.resultLock = resultLock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PenaltyDto> list(UUID raceId, UUID participantId) {
        requireParticipant(raceId, participantId);
        return penaltyRepository.findByRaceIdAndParticipantIdOrderByCreatedAtAsc(raceId, participantId)
            .stream()
            .map(this::toDto)
            .toList();
    }

    @Override
    @Transactional
    public PenaltyDto add(UUID raceId, UUID participantId, PenaltyRequest request, String actorEmail) {
        Participant participant = requireParticipant(raceId, participantId);
        resultLock.assertOpen(raceId);
        if (request.getSeconds() == null || request.getSeconds() == 0) {
            throw new ApiException("Penalty seconds must be a non-zero value");
        }
        if (request.getReason() == null || request.getReason().trim().length() < 5) {
            throw new ApiException("A reason of at least 5 characters is required for every penalty", "PENALTY_REASON_REQUIRED");
        }

        ParticipantPenalty penalty = ParticipantPenalty.builder()
            .race(participant.getRace())
            .participant(participant)
            .seconds(request.getSeconds())
            .reason(request.getReason().trim())
            .createdBy(resolveUserId(actorEmail))
            .build();

        return toDto(penaltyRepository.save(penalty));
    }

    @Override
    @Transactional
    public void remove(UUID raceId, UUID participantId, UUID penaltyId) {
        ParticipantPenalty penalty = penaltyRepository.findByRaceIdAndId(raceId, penaltyId)
            .filter(found -> found.getParticipant() != null && found.getParticipant().getId().equals(participantId))
            .orElseThrow(() -> new ResourceNotFoundException("Penalty not found"));
        resultLock.assertOpen(raceId);
        penaltyRepository.delete(penalty);
    }

    private Participant requireParticipant(UUID raceId, UUID participantId) {
        return participantRepository.findByRaceIdAndId(raceId, participantId)
            .orElseThrow(() -> new ResourceNotFoundException("Participant not found"));
    }

    private UUID resolveUserId(String actorEmail) {
        if (actorEmail == null) {
            return null;
        }
        return userRepository.findByEmailIgnoreCase(actorEmail).map(user -> user.getId()).orElse(null);
    }

    private PenaltyDto toDto(ParticipantPenalty penalty) {
        PenaltyDto dto = new PenaltyDto();
        dto.setId(penalty.getId());
        dto.setParticipantId(penalty.getParticipant() != null ? penalty.getParticipant().getId() : null);
        dto.setSeconds(penalty.getSeconds());
        dto.setReason(penalty.getReason());
        dto.setCreatedAt(penalty.getCreatedAt());
        return dto;
    }
}
