package com.racetracker.service.impl;

import com.racetracker.dto.result.RaceResultDto;
import com.racetracker.dto.result.RaceResultPageDto;
import com.racetracker.dto.result.ResultSplitDto;
import com.racetracker.exception.ApiException;
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
import com.racetracker.repository.RaceResultRowProjection;
import com.racetracker.repository.RaceResultRevisionRepository;
import org.springframework.data.domain.PageRequest;
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
    private final RaceResultRevisionRepository resultRevisionRepository;

    public ResultServiceImpl(
        RaceRepository raceRepository,
        ParticipantRepository participantRepository,
        CheckpointRecordRepository checkpointRecordRepository,
        UserRepository userRepository,
        ParticipantMapper participantMapper,
        RaceResultRevisionRepository resultRevisionRepository
    ) {
        this.raceRepository = raceRepository;
        this.participantRepository = participantRepository;
        this.checkpointRecordRepository = checkpointRecordRepository;
        this.userRepository = userRepository;
        this.participantMapper = participantMapper;
        this.resultRevisionRepository = resultRevisionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RaceResultDto> listByRace(UUID raceId) {
        return listByRacePage(raceId, 0, 100).items();
    }

    @Override
    @Transactional(readOnly = true)
    public RaceResultPageDto listByRacePage(UUID raceId, int page, int size) {
        return listByRacePage(raceId, page, size, null);
    }

    @Override
    @Transactional(readOnly = true)
    public RaceResultPageDto listByRacePage(UUID raceId, int page, int size, Long expectedRevision) {
        requireRace(raceId);
        return resultPage(raceId, null, false, page, size, expectedRevision);
    }

    @Override
    @Transactional(readOnly = true)
    public RaceResultPageDto listPublishedByRacePage(UUID raceId, int page, int size) {
        return listPublishedByRacePage(raceId, page, size, null);
    }

    @Override
    @Transactional(readOnly = true)
    public RaceResultPageDto listPublishedByRacePage(UUID raceId, int page, int size, Long expectedRevision) {
        Race race = requireRace(raceId);
        if (race.getResultsPublishedAt() == null) {
            throw new ResourceNotFoundException("Race results are not published");
        }
        return resultPage(raceId, null, true, page, size, expectedRevision);
    }

    @Override
    @Transactional(readOnly = true)
    public RaceResultDto getByRaceAndParticipant(UUID raceId, UUID participantId) {
        Race race = raceRepository.findById(raceId)
            .orElseThrow(() -> new ResourceNotFoundException("Race not found"));

        Participant participant = participantRepository.findByRaceIdAndId(raceId, participantId)
            .orElseThrow(() -> new ResourceNotFoundException("Participant not found"));

        List<RaceResultRowProjection> rows = participantRepository.findResultRows(
            raceId, null, false, participantId, PageRequest.of(0, 1)
        );
        return assembleResults(rows).stream().findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Result not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RaceResultDto> getMyResults() {
        return getMyResultsPage(0, 100).items();
    }

    @Override
    @Transactional(readOnly = true)
    public RaceResultPageDto getMyResultsPage(int page, int size) {
        User user = resolveCurrentUser();
        return resultPage(null, user.getId(), false, page, size, null);
    }

    @Override
    @Transactional
    public void publish(UUID raceId) {
        Race race = raceRepository.findById(raceId)
            .orElseThrow(() -> new ResourceNotFoundException("Race not found"));

        if (race.getStatus() != Race.RaceStatus.COMPLETED) {
            race.setStatus(Race.RaceStatus.COMPLETED);
        }
        if (race.getResultsPublishedAt() == null) race.setResultsPublishedAt(OffsetDateTime.now());
        raceRepository.save(race);
    }

    private RaceResultPageDto resultPage(UUID raceId, UUID userId, boolean publishedOnly, int page, int size, Long expectedRevision) {
        if (page < 0 || size < 1) throw new ApiException("Page must be non-negative and size must be positive");
        int boundedSize = Math.min(size, 100);
        long revision = raceId == null ? 0 : resultRevisionRepository.currentRevision(raceId);
        if (raceId != null && page > 0 && expectedRevision == null) {
            throw new ApiException("The result revision is required when requesting subsequent pages");
        }
        if (expectedRevision != null && expectedRevision != revision) {
            throw new com.racetracker.exception.ConflictException("Results changed; restart pagination from the first page");
        }
        List<RaceResultRowProjection> projections = participantRepository.findResultRows(
            raceId, userId, publishedOnly, null, PageRequest.of(page, boundedSize)
        );
        long total = participantRepository.countResultRows(raceId, userId, publishedOnly);
        return new RaceResultPageDto(assembleResults(projections), page, boundedSize, total,
            total == 0 ? 0 : (int) ((total + boundedSize - 1) / boundedSize), revision);
    }

    private List<RaceResultDto> assembleResults(List<RaceResultRowProjection> projections) {
        if (projections.isEmpty()) return List.of();
        List<UUID> participantIds = projections.stream().map(RaceResultRowProjection::getParticipantId).toList();
        Map<UUID, Participant> participantById = participantRepository.findByIdIn(participantIds).stream()
            .collect(Collectors.toMap(Participant::getId, participant -> participant));
        Map<UUID, List<CheckpointRecord>> recordsByParticipant = checkpointRecordRepository
            .findByParticipantIdInOrderByRecordedAtAsc(participantIds)
            .stream()
            .collect(Collectors.groupingBy(record -> record.getParticipant().getId()));

        List<RaceResultDto> results = new ArrayList<>(projections.size());
        for (RaceResultRowProjection projection : projections) {
            Participant participant = participantById.get(projection.getParticipantId());
            if (participant == null || participant.getRace() == null) continue;
            ResultComputation computed = computeResult(
                participant.getRace(), participant,
                recordsByParticipant.getOrDefault(participant.getId(), List.of())
            );
            RaceResultDto dto = computed.dto();
            dto.setOverallPosition(toPosition(projection.getOverallPosition()));
            dto.setGenderPosition(toPosition(projection.getGenderPosition()));
            dto.setCategoryPosition(toPosition(projection.getCategoryPosition()));
            results.add(dto);
        }
        return List.copyOf(results);
    }

    private Race requireRace(UUID raceId) {
        return raceRepository.findById(raceId)
            .orElseThrow(() -> new ResourceNotFoundException("Race not found"));
    }

    private Integer toPosition(Long position) {
        return position == null ? null : Math.toIntExact(position);
    }

    private ResultComputation computeResult(Race race, Participant participant) {
        List<CheckpointRecord> records = checkpointRecordRepository
            .findByParticipantIdAndRaceIdOrderByRecordedAtAsc(participant.getId(), race.getId());
        return computeResult(race, participant, records);
    }

    private ResultComputation computeResult(Race race, Participant participant, List<CheckpointRecord> records) {

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
