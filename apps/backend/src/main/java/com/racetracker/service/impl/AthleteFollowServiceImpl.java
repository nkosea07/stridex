package com.racetracker.service.impl;

import com.racetracker.dto.favorite.FavoriteAthleteDto;
import com.racetracker.dto.favorite.FavoriteCreateRequest;
import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.exception.UnauthorizedException;
import com.racetracker.model.AthleteFollow;
import com.racetracker.model.Participant;
import com.racetracker.model.Race;
import com.racetracker.model.User;
import com.racetracker.repository.AthleteFollowRepository;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.RaceRepository;
import com.racetracker.repository.UserRepository;
import com.racetracker.service.AthleteFollowService;
import com.racetracker.service.LastCheckpointNameCache;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class AthleteFollowServiceImpl implements AthleteFollowService {

    private final AthleteFollowRepository followRepository;
    private final ParticipantRepository participantRepository;
    private final RaceRepository raceRepository;
    private final UserRepository userRepository;
    private final LastCheckpointNameCache lastCheckpointNameCache;

    public AthleteFollowServiceImpl(
        AthleteFollowRepository followRepository,
        ParticipantRepository participantRepository,
        RaceRepository raceRepository,
        UserRepository userRepository,
        LastCheckpointNameCache lastCheckpointNameCache
    ) {
        this.followRepository = followRepository;
        this.participantRepository = participantRepository;
        this.raceRepository = raceRepository;
        this.userRepository = userRepository;
        this.lastCheckpointNameCache = lastCheckpointNameCache;
    }

    @Override
    @Transactional(readOnly = true)
    public List<FavoriteAthleteDto> listFavorites() {
        User user = resolveCurrentUser();
        return followRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
            .map(follow -> toDto(follow.getParticipant()))
            .toList();
    }

    @Override
    @Transactional
    public FavoriteAthleteDto follow(FavoriteCreateRequest request) {
        User user = resolveCurrentUser();
        Race race = raceRepository.findById(request.getRaceId())
            .orElseThrow(() -> new ResourceNotFoundException("Race not found"));
        Participant participant = participantRepository.findByRaceIdAndId(request.getRaceId(), request.getParticipantId())
            .orElseThrow(() -> new ResourceNotFoundException("Participant not found"));

        return followRepository.findByUserIdAndParticipantId(user.getId(), participant.getId())
            .map(existing -> toDto(existing.getParticipant()))
            .orElseGet(() -> {
                AthleteFollow saved = followRepository.save(AthleteFollow.builder()
                    .user(user)
                    .race(race)
                    .participant(participant)
                    .build());
                return toDto(saved.getParticipant());
            });
    }

    @Override
    @Transactional
    public void unfollow(UUID raceId, UUID participantId) {
        User user = resolveCurrentUser();
        followRepository.findByUserIdAndParticipantId(user.getId(), participantId)
            .ifPresent(followRepository::delete);
    }

    private FavoriteAthleteDto toDto(Participant participant) {
        FavoriteAthleteDto dto = new FavoriteAthleteDto();
        Race race = participant.getRace();
        dto.setParticipantId(participant.getId());
        dto.setParticipantName(participant.getFullName());
        dto.setBibNumber(participant.getBibNumber());
        dto.setStatus(participant.getStatus() != null ? participant.getStatus().name() : null);
        if (race != null) {
            dto.setRaceId(race.getId());
            dto.setRaceName(race.getName());
            dto.setLastCheckpoint(lastCheckpointNameCache.getName(participant.getId(), race.getId()));
            BigDecimal covered = participant.getTotalDistance() != null ? participant.getTotalDistance() : BigDecimal.ZERO;
            if (race.getDistanceKm() != null) {
                dto.setDistanceRemaining(race.getDistanceKm().subtract(covered).max(BigDecimal.ZERO));
            }
        }
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
