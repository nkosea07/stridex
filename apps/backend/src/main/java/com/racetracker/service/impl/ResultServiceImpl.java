package com.racetracker.service.impl;

import com.racetracker.dto.result.RaceResultDto;
import com.racetracker.dto.result.ResultSplitDto;
import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.exception.UnauthorizedException;
import com.racetracker.mapper.ParticipantMapper;
import com.racetracker.model.Checkpoint;
import com.racetracker.model.CheckpointRecord;
import com.racetracker.model.Participant;
import com.racetracker.model.Race;
import com.racetracker.model.User;
import com.racetracker.repository.CheckpointRecordRepository;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.RaceRepository;
import com.racetracker.repository.UserRepository;
import com.racetracker.service.ResultService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ResultServiceImpl implements ResultService {

    private final RaceRepository raceRepository;
    private final ParticipantRepository participantRepository;
    private final CheckpointRecordRepository checkpointRecordRepository;
    private final UserRepository userRepository;
    private final ParticipantMapper participantMapper;

    public ResultServiceImpl(
        RaceRepository raceRepository,
        ParticipantRepository participantRepository,
        CheckpointRecordRepository checkpointRecordRepository,
        UserRepository userRepository,
        ParticipantMapper participantMapper
    ) {
        this.raceRepository = raceRepository;
        this.participantRepository = participantRepository;
        this.checkpointRecordRepository = checkpointRecordRepository;
        this.userRepository = userRepository;
        this.participantMapper = participantMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RaceResultDto> listByRace(UUID raceId) {
        Race race = raceRepository.findById(raceId)
            .orElseThrow(() -> new ResourceNotFoundException("Race not found"));

        List<Participant> participants = participantRepository.findByRaceId(raceId);
        List<ResultComputation> rows = participants.stream()
            .map(p -> computeResult(race, p))
            .toList();

        assignPositions(rows);

        return rows.stream()
            .map(ResultComputation::dto)
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public RaceResultDto getByRaceAndParticipant(UUID raceId, UUID participantId) {
        Race race = raceRepository.findById(raceId)
            .orElseThrow(() -> new ResourceNotFoundException("Race not found"));

        Participant participant = participantRepository.findByRaceIdAndId(raceId, participantId)
            .orElseThrow(() -> new ResourceNotFoundException("Participant not found"));

        List<RaceResultDto> all = listByRace(raceId);
        return all.stream()
            .filter(r -> participantId.equals(r.getParticipantId()))
            .findFirst()
            .orElseGet(() -> computeResult(race, participant).dto());
    }

    @Override
    @Transactional(readOnly = true)
    public List<RaceResultDto> getMyResults() {
        User user = resolveCurrentUser();
        List<Participant> participants = participantRepository.findByUserIdOrderByRegisteredAtDesc(user.getId());

        return participants.stream()
            .map(participant -> getByRaceAndParticipant(participant.getRace().getId(), participant.getId()))
            .toList();
    }

    @Override
    @Transactional
    public void publish(UUID raceId) {
        Race race = raceRepository.findById(raceId)
            .orElseThrow(() -> new ResourceNotFoundException("Race not found"));

        if (race.getStatus() != Race.RaceStatus.COMPLETED) {
            race.setStatus(Race.RaceStatus.COMPLETED);
            raceRepository.save(race);
        }
    }

    private ResultComputation computeResult(Race race, Participant participant) {
        List<CheckpointRecord> records = checkpointRecordRepository
            .findByParticipantIdAndRaceIdOrderByRecordedAtAsc(participant.getId(), race.getId());

        RaceResultDto dto = new RaceResultDto();
        dto.setId(participant.getId());
        dto.setParticipantId(participant.getId());
        dto.setRaceId(race.getId());
        dto.setCategoryId(participant.getCategory() != null ? participant.getCategory().getId() : null);
        dto.setParticipant(participantMapper.toDto(participant));
        dto.setBibNumber(participant.getBibNumber());
        dto.setCategory(participant.getCategory() != null ? participant.getCategory().getName() : null);
        dto.setStatus(mapStatus(participant.getStatus()));
        dto.setDisqualified(participant.getStatus() == Participant.ParticipantStatus.DISQUALIFIED);
        dto.setDisqualificationReason(dto.isDisqualified() ? "Disqualified" : null);

        OffsetDateTime start = resolveStart(participant, records);
        OffsetDateTime finish = resolveFinish(records);

        dto.setStartTime(start != null ? start.toString() : null);
        dto.setFinishTime(finish != null ? finish.toString() : null);

        Duration duration = (start != null && finish != null && !finish.isBefore(start))
            ? Duration.between(start, finish)
            : null;

        String durationStr = formatDuration(duration);
        dto.setTotalTime(durationStr);
        dto.setGunTime(durationStr);
        dto.setChipTime(durationStr);

        if (duration != null && race.getDistanceKm() != null && race.getDistanceKm().doubleValue() > 0) {
            double hours = duration.toSeconds() / 3600.0;
            if (hours > 0) {
                double speed = race.getDistanceKm().doubleValue() / hours;
                dto.setAverageSpeed(round2(speed));
                dto.setAveragePace(formatPace(duration, race.getDistanceKm().doubleValue()));
            }
        }

        dto.setOfficial(participant.getStatus() == Participant.ParticipantStatus.FINISHED && !dto.isDisqualified());

        if (start != null) {
            for (CheckpointRecord record : records) {
                ResultSplitDto split = new ResultSplitDto();
                split.setId(record.getId());
                split.setCheckpointId(record.getCheckpoint() != null ? record.getCheckpoint().getId() : null);
                split.setTimestamp(record.getRecordedAt() != null ? record.getRecordedAt().toString() : null);
                split.setMethod(record.getRecordMethod() != null ? record.getRecordMethod().name().toLowerCase(Locale.ROOT) : null);
                if (record.getRecordedAt() != null && !record.getRecordedAt().isBefore(start)) {
                    split.setSplitTime(formatDuration(Duration.between(start, record.getRecordedAt())));
                }
                dto.getSplits().add(split);
            }
        }

        Long finishSeconds = duration != null ? duration.toSeconds() : null;
        return new ResultComputation(dto, finishSeconds);
    }

    private void assignPositions(List<ResultComputation> rows) {
        List<ResultComputation> finished = rows.stream()
            .filter(r -> r.finishSeconds() != null && "finished".equals(r.dto().getStatus()) && !r.dto().isDisqualified())
            .sorted(Comparator.comparingLong(ResultComputation::finishSeconds))
            .toList();

        for (int i = 0; i < finished.size(); i++) {
            finished.get(i).dto().setOverallPosition(i + 1);
        }

        Map<String, List<ResultComputation>> byGender = finished.stream()
            .collect(Collectors.groupingBy(r -> {
                String gender = r.dto().getParticipant() != null ? r.dto().getParticipant().getGender() : null;
                return gender == null ? "UNKNOWN" : gender;
            }));
        byGender.values().forEach(list -> {
            list.sort(Comparator.comparingLong(ResultComputation::finishSeconds));
            for (int i = 0; i < list.size(); i++) {
                list.get(i).dto().setGenderPosition(i + 1);
            }
        });

        Map<UUID, List<ResultComputation>> byCategory = finished.stream()
            .collect(Collectors.groupingBy(r -> r.dto().getCategoryId()));
        byCategory.values().forEach(list -> {
            list.sort(Comparator.comparingLong(ResultComputation::finishSeconds));
            for (int i = 0; i < list.size(); i++) {
                list.get(i).dto().setCategoryPosition(i + 1);
            }
        });
    }

    private OffsetDateTime resolveStart(Participant participant, List<CheckpointRecord> records) {
        if (participant.getCheckedInAt() != null) {
            return participant.getCheckedInAt();
        }

        for (CheckpointRecord record : records) {
            if (record.getCheckpoint() != null && record.getCheckpoint().getCheckpointType() == Checkpoint.CheckpointType.START) {
                return record.getRecordedAt();
            }
        }

        return participant.getRegisteredAt();
    }

    private OffsetDateTime resolveFinish(List<CheckpointRecord> records) {
        for (int i = records.size() - 1; i >= 0; i--) {
            CheckpointRecord record = records.get(i);
            if (record.getCheckpoint() != null && record.getCheckpoint().getCheckpointType() == Checkpoint.CheckpointType.FINISH) {
                return record.getRecordedAt();
            }
        }
        return null;
    }

    private String mapStatus(Participant.ParticipantStatus status) {
        if (status == null) return "in_progress";
        return switch (status) {
            case FINISHED -> "finished";
            case DNF -> "dnf";
            case DNS -> "dns";
            case DISQUALIFIED -> "dq";
            default -> "in_progress";
        };
    }

    private String formatDuration(Duration duration) {
        if (duration == null) {
            return null;
        }
        long totalSeconds = duration.toSeconds();
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    private String formatPace(Duration duration, double distanceKm) {
        if (duration == null || distanceKm <= 0) {
            return null;
        }
        double totalSeconds = duration.toSeconds();
        double secondsPerKm = totalSeconds / distanceKm;
        long minutes = (long) (secondsPerKm / 60);
        long seconds = Math.round(secondsPerKm % 60);
        if (seconds == 60) {
            minutes += 1;
            seconds = 0;
        }
        return String.format("%02d:%02d/km", minutes, seconds);
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private User resolveCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new UnauthorizedException("Unauthorized");
        }

        return userRepository.findByEmailIgnoreCase(authentication.getName())
            .orElseThrow(() -> new UnauthorizedException("Unauthorized"));
    }

    private record ResultComputation(RaceResultDto dto, Long finishSeconds) {}
}
