package com.racetracker.service.impl;

import com.racetracker.dto.result.RaceResultDto;
import com.racetracker.dto.result.RaceResultPageDto;
import com.racetracker.dto.result.ResultSplitDto;
import com.racetracker.exception.ApiException;
import com.racetracker.exception.ConflictException;
import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.exception.UnauthorizedException;
import com.racetracker.mapper.ParticipantMapper;
import com.racetracker.model.Checkpoint;
import com.racetracker.model.CheckpointRecord;
import com.racetracker.model.Participant;
import com.racetracker.model.Race;
import com.racetracker.model.RaceResult;
import com.racetracker.model.User;
import com.racetracker.repository.CheckpointRecordRepository;
import com.racetracker.repository.ParticipantPenaltyRepository;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.RaceCategoryRepository;
import com.racetracker.repository.RaceRepository;
import com.racetracker.repository.RaceResultRepository;
import com.racetracker.repository.UserRepository;
import com.racetracker.repository.RaceResultRowProjection;
import com.racetracker.repository.RaceResultRevisionRepository;
import com.racetracker.security.CapabilityAuthorizationService;
import com.racetracker.model.UserCapabilityType;
import org.springframework.data.domain.PageRequest;
import com.racetracker.service.CheckpointCutoff;
import com.racetracker.service.ResultService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
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
    private final ParticipantPenaltyRepository penaltyRepository;
    private final RaceResultRepository raceResultRepository;
    private final RaceCategoryRepository raceCategoryRepository;
    private final CapabilityAuthorizationService capabilityAuthorizationService;
    private final com.racetracker.service.ResultVersioning resultVersioning;
    private final com.racetracker.service.ResultLock resultLock;
    private final com.racetracker.repository.RaceResultVersionRepository resultVersionRepository;
    private final com.racetracker.repository.ResultQueryRepository resultQueryRepository;

    public ResultServiceImpl(
        RaceRepository raceRepository,
        ParticipantRepository participantRepository,
        CheckpointRecordRepository checkpointRecordRepository,
        UserRepository userRepository,
        ParticipantMapper participantMapper,
        RaceResultRevisionRepository resultRevisionRepository,
        ParticipantPenaltyRepository penaltyRepository,
        RaceResultRepository raceResultRepository,
        RaceCategoryRepository raceCategoryRepository,
        CapabilityAuthorizationService capabilityAuthorizationService,
        com.racetracker.service.ResultVersioning resultVersioning,
        com.racetracker.service.ResultLock resultLock,
        com.racetracker.repository.RaceResultVersionRepository resultVersionRepository,
        com.racetracker.repository.ResultQueryRepository resultQueryRepository
    ) {
        this.raceRepository = raceRepository;
        this.participantRepository = participantRepository;
        this.checkpointRecordRepository = checkpointRecordRepository;
        this.userRepository = userRepository;
        this.participantMapper = participantMapper;
        this.resultRevisionRepository = resultRevisionRepository;
        this.penaltyRepository = penaltyRepository;
        this.raceResultRepository = raceResultRepository;
        this.raceCategoryRepository = raceCategoryRepository;
        this.capabilityAuthorizationService = capabilityAuthorizationService;
        this.resultVersioning = resultVersioning;
        this.resultLock = resultLock;
        this.resultVersionRepository = resultVersionRepository;
        this.resultQueryRepository = resultQueryRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RaceResultDto> listByRace(UUID raceId) {
        requireRace(raceId);
        return collectAllResultPages(raceId, null, false);
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

        if (race.getResultsPublishedAt() != null) {
            Optional<RaceResult> persisted = raceResultRepository.findByRaceIdAndParticipantId(raceId, participantId);
            if (persisted.isPresent()) {
                return assemblePersistedResults(List.of(persisted.get())).stream().findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("Result not found"));
            }
        }

        List<RaceResultRowProjection> rows = participantRepository.findResultRows(
            raceId, null, false, participantId, PageRequest.of(0, 1)
        );
        return assembleResults(rows).stream().findFirst()
            .orElseThrow(() -> new ResourceNotFoundException("Result not found"));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RaceResultDto> getMyResults() {
        capabilityAuthorizationService.require(UserCapabilityType.ATHLETE);
        User user = resolveCurrentUser();
        return collectAllResultPages(null, user.getId(), false);
    }

    /**
     * Iterates every result page so callers that need the full set (exports, team/country
     * rankings) are never silently truncated at a single page limit.
     */
    private List<RaceResultDto> collectAllResultPages(UUID raceId, UUID userId, boolean publishedOnly) {
        List<RaceResultDto> all = new ArrayList<>();
        int page = 0;
        int size = 100;
        Long revision = null;
        RaceResultPageDto resultPage;
        do {
            resultPage = resultPage(raceId, userId, publishedOnly, page, size, revision);
            all.addAll(resultPage.items());
            revision = resultPage.revision();
            page++;
        } while (!resultPage.items().isEmpty() && all.size() < resultPage.totalElements());
        return all;
    }

    @Override
    @Transactional(readOnly = true)
    public RaceResultPageDto getMyResultsPage(int page, int size) {
        capabilityAuthorizationService.require(UserCapabilityType.ATHLETE);
        User user = resolveCurrentUser();
        return resultPage(null, user.getId(), false, page, size, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<com.racetracker.dto.result.AthleteHistoryEntryDto> getMyHistory() {
        capabilityAuthorizationService.require(UserCapabilityType.ATHLETE);
        User user = resolveCurrentUser();
        return raceResultRepository.findAllByUserId(user.getId()).stream()
            .map(row -> new com.racetracker.dto.result.AthleteHistoryEntryDto(
                row.getRace().getId(), row.getRace().getName(), row.getRace().getRaceDate(),
                row.getRace().getDistanceKm(),
                row.getChipTime() != null ? formatDuration(row.getChipTime()) : null,
                row.getOverallPosition(), row.getCategoryPosition(),
                Boolean.TRUE.equals(row.getIsOfficial()), Boolean.TRUE.equals(row.getIsDisqualified()),
                medalFor(row.getOverallPosition() == null ? null : row.getOverallPosition().longValue(),
                    row.getRace().getMedalPlaces(), Boolean.TRUE.equals(row.getIsOfficial()),
                    Boolean.TRUE.equals(row.getIsDisqualified())),
                qualificationFor(row.getOverallPosition() == null ? null : row.getOverallPosition().longValue(),
                    row.getRace().getMedalPlaces(),
                    Boolean.TRUE.equals(row.getIsOfficial()), Boolean.TRUE.equals(row.getIsDisqualified()))))
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<com.racetracker.dto.result.PersonalBestDto> getMyPersonalBests() {
        capabilityAuthorizationService.require(UserCapabilityType.ATHLETE);
        User user = resolveCurrentUser();
        Map<java.math.BigDecimal, RaceResult> best = new java.util.LinkedHashMap<>();
        Map<java.math.BigDecimal, Integer> counts = new java.util.HashMap<>();
        for (RaceResult row : raceResultRepository.findAllByUserId(user.getId())) {
            if (!Boolean.TRUE.equals(row.getIsOfficial()) || Boolean.TRUE.equals(row.getIsDisqualified())
                || row.getChipTime() == null || row.getRace().getDistanceKm() == null) {
                continue;
            }
            java.math.BigDecimal distance = row.getRace().getDistanceKm();
            counts.merge(distance, 1, Integer::sum);
            RaceResult current = best.get(distance);
            if (current == null || row.getChipTime().compareTo(current.getChipTime()) < 0) {
                best.put(distance, row);
            }
        }
        return best.entrySet().stream()
            .map(entry -> new com.racetracker.dto.result.PersonalBestDto(entry.getKey(),
                formatDuration(entry.getValue().getChipTime()), entry.getValue().getRace().getId(),
                entry.getValue().getRace().getName(), counts.getOrDefault(entry.getKey(), 1)))
            .sorted(java.util.Comparator.comparing(com.racetracker.dto.result.PersonalBestDto::distanceKm))
            .toList();
    }

    @Override
    @Transactional
    public int publish(UUID raceId) {
        Race race = raceRepository.findByIdForUpdate(raceId)
            .orElseThrow(() -> new ResourceNotFoundException("Race not found"));

        if (race.getStatus() != Race.RaceStatus.COMPLETED) {
            throw new ConflictException("Race must be completed before results can be published");
        }
        resultLock.assertOpen(race);

        List<RaceResultDto> computed = computeAllResults(raceId);
        raceResultRepository.deleteByRaceId(raceId);
        List<RaceResult> snapshots = computed.stream().map(dto -> toSnapshot(race, dto)).toList();
        raceResultRepository.saveAll(snapshots);
        com.racetracker.model.RaceResultVersion version =
            resultVersioning.record(raceId, snapshots, currentUserIdOrNull());

        if (race.getResultsPublishedAt() == null) {
            race.setResultsPublishedAt(OffsetDateTime.now());
            raceRepository.save(race);
        }
        return version.getVersion();
    }

    @Override
    @Transactional
    public com.racetracker.dto.result.ResultVersionDto certify(UUID raceId) {
        Race race = raceRepository.findByIdForUpdate(raceId)
            .orElseThrow(() -> new ResourceNotFoundException("Race not found"));
        resultLock.assertOpen(race);
        com.racetracker.model.RaceResultVersion latest = resultVersionRepository.findTopByRaceIdOrderByVersionDesc(raceId)
            .orElseThrow(() -> new ConflictException("Publish the results before certifying them", "RESULTS_NOT_PUBLISHED"));
        String persisted = resultVersioning.contentHash(resultVersioning.lines(raceResultRepository.findByRaceIdOrderByOverallPositionAsc(raceId)));
        if (!persisted.equals(latest.getContentSha256())) {
            throw new ConflictException("The published results no longer match their recorded version", "RESULTS_INTEGRITY_FAILED");
        }
        if (!currentComputedHash(race).equals(latest.getContentSha256())) {
            throw new ConflictException("Timing changed after the last publish. Publish again, review, then certify.",
                "RESULTS_CHANGED_SINCE_PUBLISH");
        }
        if (resultQueryRepository.countByRaceIdAndStatus(raceId, com.racetracker.model.ResultQuery.Status.OPEN) > 0) {
            throw new ConflictException("Answer every open result query before certifying the results", "RESULT_QUERIES_OPEN");
        }
        UUID actor = currentUserIdOrNull();
        OffsetDateTime now = OffsetDateTime.now();
        latest.setStatus(com.racetracker.model.RaceResultVersion.Status.OFFICIAL);
        latest.setCertifiedAt(now);
        latest.setCertifiedBy(actor);
        resultVersionRepository.save(latest);
        race.setResultsCertifiedAt(now);
        race.setResultsCertifiedBy(actor);
        raceRepository.save(race);
        return toVersionDto(latest);
    }

    @Override
    @Transactional
    public void reopen(UUID raceId, String reason) {
        Race race = raceRepository.findByIdForUpdate(raceId)
            .orElseThrow(() -> new ResourceNotFoundException("Race not found"));
        if (race.getResultsCertifiedAt() == null) {
            throw new ConflictException("Results are not certified", "RESULTS_NOT_CERTIFIED");
        }
        race.setResultsCertifiedAt(null);
        race.setResultsCertifiedBy(null);
        raceRepository.save(race);
    }

    @Override
    @Transactional(readOnly = true)
    public com.racetracker.dto.result.ResultIntegrityDto integrity(UUID raceId) {
        Race race = raceRepository.findById(raceId)
            .orElseThrow(() -> new ResourceNotFoundException("Race not found"));
        List<com.racetracker.model.RaceResultVersion> versions = resultVersionRepository.findByRaceIdOrderByVersionAsc(raceId);
        if (versions.isEmpty()) {
            return new com.racetracker.dto.result.ResultIntegrityDto(null, null, null, null, null, null, true, true, false);
        }
        boolean chainValid = true;
        String previousChain = null;
        for (com.racetracker.model.RaceResultVersion version : versions) {
            String content = resultVersioning.contentHash(resultVersioning.snapshotLines(version));
            String chain = com.racetracker.service.ResultVersioning.chainHash(previousChain, content, version.getVersion(), raceId);
            chainValid &= content.equals(version.getContentSha256())
                && chain.equals(version.getChainSha256())
                && java.util.Objects.equals(previousChain, version.getPreviousChainSha256());
            previousChain = version.getChainSha256();
        }
        com.racetracker.model.RaceResultVersion latest = versions.get(versions.size() - 1);
        String persisted = resultVersioning.contentHash(resultVersioning.lines(raceResultRepository.findByRaceIdOrderByOverallPositionAsc(raceId)));
        boolean changed = race.getStatus() == Race.RaceStatus.COMPLETED
            && !currentComputedHash(race).equals(latest.getContentSha256());
        return new com.racetracker.dto.result.ResultIntegrityDto(
            latest.getVersion(),
            latest.getStatus().name(),
            latest.getPublishedAt().toString(),
            latest.getCertifiedAt() == null ? null : latest.getCertifiedAt().toString(),
            latest.getContentSha256(),
            latest.getChainSha256(),
            chainValid,
            persisted.equals(latest.getContentSha256()),
            changed);
    }

    @Override
    @Transactional(readOnly = true)
    public List<com.racetracker.dto.result.ResultVersionDto> versions(UUID raceId) {
        return resultVersionRepository.findByRaceIdOrderByVersionAsc(raceId).stream().map(this::toVersionDto).toList();
    }

    private String currentComputedHash(Race race) {
        List<RaceResult> recomputed = computeAllResults(race.getId()).stream().map(dto -> toSnapshot(race, dto)).toList();
        return resultVersioning.contentHash(resultVersioning.lines(recomputed));
    }

    private com.racetracker.dto.result.ResultVersionDto toVersionDto(com.racetracker.model.RaceResultVersion version) {
        return new com.racetracker.dto.result.ResultVersionDto(
            version.getVersion(),
            version.getStatus().name(),
            version.getResultCount(),
            version.getContentSha256(),
            version.getChainSha256(),
            version.getPublishedAt().toString(),
            version.getCertifiedAt() == null ? null : version.getCertifiedAt().toString());
    }

    private UUID currentUserIdOrNull() {
        org.springframework.security.core.Authentication authentication =
            org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        return userRepository.findByEmailIgnoreCase(authentication.getName()).map(com.racetracker.model.User::getId).orElse(null);
    }

    private List<RaceResultDto> computeAllResults(UUID raceId) {
        List<RaceResultDto> all = new ArrayList<>();
        int page = 0;
        int size = 100;
        RaceResultPageDto resultPage;
        do {
            resultPage = computeResultPage(raceId, null, false, page, size, 0L);
            all.addAll(resultPage.items());
            page++;
        } while (!resultPage.items().isEmpty() && all.size() < resultPage.totalElements());
        return all;
    }

    private RaceResult toSnapshot(Race race, RaceResultDto dto) {
        RaceResult result = new RaceResult();
        result.setRace(race);
        result.setParticipant(participantRepository.getReferenceById(dto.getParticipantId()));
        if (dto.getCategoryId() != null) {
            result.setCategory(raceCategoryRepository.getReferenceById(dto.getCategoryId()));
        }
        result.setGunTime(parseDuration(dto.getGunTime()));
        result.setChipTime(parseDuration(dto.getChipTime()));
        result.setOverallPosition(dto.getOverallPosition());
        result.setGenderPosition(dto.getGenderPosition());
        result.setCategoryPosition(dto.getCategoryPosition());
        result.setAgeGroup(dto.getAgeGroup());
        result.setAgeGroupPosition(dto.getAgeGroupPosition());
        result.setPenaltySeconds(dto.getPenaltySeconds());
        result.setAveragePace(parsePace(dto.getAveragePace()));
        result.setAverageSpeed(dto.getAverageSpeed() == null ? null : BigDecimal.valueOf(dto.getAverageSpeed()));
        result.setIsOfficial(dto.isOfficial());
        result.setIsDisqualified(dto.isDisqualified());
        result.setDisqualificationReason(dto.getDisqualificationReason());
        return result;
    }

    private RaceResultPageDto resultPage(UUID raceId, UUID userId, boolean publishedOnly, int page, int size, Long expectedRevision) {
        if (page < 0 || size < 1) throw new ApiException("Page must be non-negative and size must be positive");
        int boundedSize = Math.min(size, 100);
        long revision = raceId == null ? 0 : resultRevisionRepository.currentRevision(raceId);
        if (raceId != null && page > 0 && expectedRevision == null) {
            throw new ApiException("The result revision is required when requesting subsequent pages");
        }
        if (expectedRevision != null && expectedRevision != revision) {
            throw new ConflictException("Results changed; restart pagination from the first page");
        }

        if (raceId != null && hasPersistedResults(raceId)) {
            return persistedResultPage(raceId, userId, page, boundedSize, revision);
        }
        return computeResultPage(raceId, userId, publishedOnly, page, boundedSize, revision);
    }

    private boolean hasPersistedResults(UUID raceId) {
        Race race = raceRepository.findById(raceId).orElse(null);
        return race != null
            && race.getResultsPublishedAt() != null
            && raceResultRepository.countByRaceId(raceId) > 0;
    }

    private RaceResultPageDto persistedResultPage(UUID raceId, UUID userId, int page, int size, long revision) {
        List<RaceResult> rows = raceResultRepository.findPageByRace(raceId, userId, PageRequest.of(page, size));
        long total = raceResultRepository.countPageByRace(raceId, userId);
        return new RaceResultPageDto(assemblePersistedResults(rows), page, size, total,
            total == 0 ? 0 : (int) ((total + size - 1) / size), revision);
    }

    private RaceResultPageDto computeResultPage(UUID raceId, UUID userId, boolean publishedOnly, int page, int size, long revision) {
        List<RaceResultRowProjection> projections = participantRepository.findResultRows(
            raceId, userId, publishedOnly, null, PageRequest.of(page, size)
        );
        long total = participantRepository.countResultRows(raceId, userId, publishedOnly);
        return new RaceResultPageDto(assembleResults(projections), page, size, total,
            total == 0 ? 0 : (int) ((total + size - 1) / size), revision);
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

        Map<UUID, Integer> penaltySecondsByParticipant = penaltyRepository.findByParticipantIdIn(participantIds)
            .stream()
            .collect(Collectors.groupingBy(
                penalty -> penalty.getParticipant().getId(),
                Collectors.summingInt(penalty -> penalty.getSeconds() == null ? 0 : penalty.getSeconds())
            ));

        List<RaceResultDto> results = new ArrayList<>(projections.size());
        for (RaceResultRowProjection projection : projections) {
            Participant participant = participantById.get(projection.getParticipantId());
            if (participant == null || participant.getRace() == null) continue;
            RaceResultDto dto = computeResult(
                participant.getRace(), participant,
                recordsByParticipant.getOrDefault(participant.getId(), List.of()),
                penaltySecondsByParticipant.getOrDefault(participant.getId(), 0)
            );
            dto.setOverallPosition(toPosition(projection.getOverallPosition()));
            dto.setGenderPosition(toPosition(projection.getGenderPosition()));
            dto.setCategoryPosition(toPosition(projection.getCategoryPosition()));
            dto.setAgeGroup(projection.getAgeGroup());
            dto.setAgeGroupPosition(toPosition(projection.getAgeGroupPosition()));
            dto.setMedal(medalFor(projection.getOverallPosition() == null ? null
                    : projection.getOverallPosition().longValue(), participant.getRace().getMedalPlaces(),
                dto.isOfficial(), dto.isDisqualified()));
            dto.setQualificationStatus(qualificationFor(dto.getOverallPosition() == null ? null
                    : dto.getOverallPosition().longValue(), participant.getRace().getMedalPlaces(),
                dto.isOfficial(), dto.isDisqualified()));
            results.add(dto);
        }
        return List.copyOf(results);
    }

    private List<RaceResultDto> assemblePersistedResults(List<RaceResult> rows) {
        if (rows.isEmpty()) return List.of();
        List<UUID> participantIds = rows.stream().map(row -> row.getParticipant().getId()).toList();
        Map<UUID, List<CheckpointRecord>> recordsByParticipant = checkpointRecordRepository
            .findByParticipantIdInOrderByRecordedAtAsc(participantIds)
            .stream()
            .collect(Collectors.groupingBy(record -> record.getParticipant().getId()));

        List<RaceResultDto> results = new ArrayList<>(rows.size());
        for (RaceResult row : rows) {
            Participant participant = row.getParticipant();
            if (participant == null || participant.getRace() == null) continue;
            int penaltySeconds = row.getPenaltySeconds() == null ? 0 : row.getPenaltySeconds();
            RaceResultDto dto = computeResult(
                participant.getRace(), participant,
                recordsByParticipant.getOrDefault(participant.getId(), List.of()),
                penaltySeconds
            );
            applyPersistedSnapshot(dto, row);
            results.add(dto);
        }
        return List.copyOf(results);
    }

    private void applyPersistedSnapshot(RaceResultDto dto, RaceResult row) {
        if (row.getGunTime() != null) dto.setGunTime(formatDuration(row.getGunTime()));
        if (row.getChipTime() != null) dto.setChipTime(formatDuration(row.getChipTime()));
        Duration officialTime = row.getChipTime() != null ? row.getChipTime() : row.getGunTime();
        if (officialTime != null) dto.setTotalTime(formatDuration(officialTime));
        if (row.getAveragePace() != null) dto.setAveragePace(formatPaceDuration(row.getAveragePace()));
        if (row.getAverageSpeed() != null) dto.setAverageSpeed(row.getAverageSpeed().doubleValue());
        dto.setOverallPosition(row.getOverallPosition());
        dto.setGenderPosition(row.getGenderPosition());
        dto.setCategoryPosition(row.getCategoryPosition());
        dto.setAgeGroup(row.getAgeGroup());
        dto.setAgeGroupPosition(row.getAgeGroupPosition());
        dto.setPenaltySeconds(row.getPenaltySeconds() == null ? 0 : row.getPenaltySeconds());
        boolean official = Boolean.TRUE.equals(row.getIsOfficial());
        boolean disqualified = Boolean.TRUE.equals(row.getIsDisqualified());
        dto.setOfficial(official);
        dto.setDisqualified(disqualified);
        dto.setDisqualificationReason(row.getDisqualificationReason());
        dto.setCertificateAvailable(official);
        dto.setMedal(medalFor(row.getOverallPosition() == null ? null : row.getOverallPosition().longValue(),
            row.getRace().getMedalPlaces(), official, disqualified));
        dto.setQualificationStatus(qualificationFor(row.getOverallPosition() == null ? null
                : row.getOverallPosition().longValue(), row.getRace().getMedalPlaces(), official, disqualified));
    }

    private Race requireRace(UUID raceId) {
        return raceRepository.findById(raceId)
            .orElseThrow(() -> new ResourceNotFoundException("Race not found"));
    }

    private Integer toPosition(Long position) {
        return position == null ? null : Math.toIntExact(position);
    }

    private String medalFor(Long overallPosition, Integer medalPlaces, boolean official, boolean disqualified) {
        if (!official || disqualified || overallPosition == null) {
            return null;
        }
        int places = medalPlaces == null ? 3 : Math.max(0, medalPlaces);
        if (overallPosition > places) {
            return null;
        }
        return switch (overallPosition.intValue()) {
            case 1 -> "GOLD";
            case 2 -> "SILVER";
            case 3 -> "BRONZE";
            default -> "MEDAL";
        };
    }

    private String qualificationFor(Long overallPosition, Integer medalPlaces, boolean official,
                                    boolean disqualified) {
        if (!official || disqualified || overallPosition == null) {
            return "NOT_QUALIFIED";
        }
        int places = medalPlaces == null ? 3 : Math.max(0, medalPlaces);
        return overallPosition <= places ? "QUALIFIED" : "NOT_QUALIFIED";
    }

    private RaceResultDto computeResult(Race race, Participant participant, List<CheckpointRecord> records, int penaltySeconds) {

        RaceResultDto dto = new RaceResultDto();
        dto.setId(participant.getId());
        dto.setParticipantId(participant.getId());
        dto.setRaceId(race.getId());
        dto.setCategoryId(participant.getCategory() != null ? participant.getCategory().getId() : null);
        dto.setParticipant(participantMapper.toResultDto(participant));
        dto.setBibNumber(participant.getBibNumber());
        dto.setCategory(participant.getCategory() != null ? participant.getCategory().getName() : null);
        dto.setStatus(mapStatus(participant.getStatus()));
        dto.setDisqualified(participant.getStatus() == Participant.ParticipantStatus.DISQUALIFIED);
        dto.setDisqualificationReason(dto.isDisqualified()
            ? (participant.getStatusReason() != null && !participant.getStatusReason().isBlank()
                ? participant.getStatusReason() : "Disqualified")
            : null);
        dto.setPenaltySeconds(penaltySeconds);

        OffsetDateTime start = CheckpointCutoff.resolveStart(participant, records);
        OffsetDateTime gunStart = CheckpointCutoff.gunStart(participant);
        OffsetDateTime finish = resolveFinish(records);

        dto.setStartTime(start != null ? start.toString() : null);
        dto.setFinishTime(finish != null ? finish.toString() : null);

        Duration duration = elapsedWithPenalty(start, finish, penaltySeconds);
        Duration gunDuration = elapsedWithPenalty(gunStart, finish, penaltySeconds);

        String durationStr = formatDuration(duration);
        dto.setTotalTime(durationStr);
        dto.setChipTime(durationStr);
        dto.setGunTime(gunDuration != null ? formatDuration(gunDuration) : durationStr);

        if (duration != null && race.getDistanceKm() != null && race.getDistanceKm().doubleValue() > 0) {
            double hours = duration.toSeconds() / 3600.0;
            if (hours > 0) {
                double speed = race.getDistanceKm().doubleValue() / hours;
                dto.setAverageSpeed(round2(speed));
                dto.setAveragePace(formatPace(duration, race.getDistanceKm().doubleValue()));
            }
        }

        dto.setOfficial(participant.getStatus() == Participant.ParticipantStatus.FINISHED && !dto.isDisqualified());
        dto.setCertificateAvailable(dto.isOfficial());

        if (start != null) {
            for (CheckpointRecord record : records) {
                OffsetDateTime effective = effectiveTime(record);
                ResultSplitDto split = new ResultSplitDto();
                split.setId(record.getId());
                split.setCheckpointId(record.getCheckpoint() != null ? record.getCheckpoint().getId() : null);
                split.setTimestamp(effective != null ? effective.toString() : null);
                split.setMethod(record.getRecordMethod() != null ? record.getRecordMethod().name().toLowerCase(Locale.ROOT) : null);
                if (effective != null && !effective.isBefore(start)) {
                    split.setSplitTime(formatDuration(Duration.between(start, effective)));
                }
                dto.getSplits().add(split);
            }
        }

        return dto;
    }

    private OffsetDateTime effectiveTime(CheckpointRecord record) {
        return CheckpointCutoff.effectiveTime(record);
    }

    private Duration elapsedWithPenalty(OffsetDateTime start, OffsetDateTime finish, int penaltySeconds) {
        if (start == null || finish == null || finish.isBefore(start)) {
            return null;
        }
        return Duration.between(start, finish).plusSeconds(penaltySeconds);
    }

    private OffsetDateTime resolveFinish(List<CheckpointRecord> records) {
        for (int i = records.size() - 1; i >= 0; i--) {
            CheckpointRecord record = records.get(i);
            if (com.racetracker.service.CheckpointPasses.isFinish(record)) {
                return effectiveTime(record);
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
        return formatPaceDuration(Duration.ofSeconds(Math.round(secondsPerKm)));
    }

    private String formatPaceDuration(Duration perKm) {
        if (perKm == null) {
            return null;
        }
        long totalSeconds = perKm.toSeconds();
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d/km", minutes, seconds);
    }

    private Duration parseDuration(String formatted) {
        if (formatted == null || formatted.isBlank()) {
            return null;
        }
        String[] parts = formatted.split(":");
        try {
            if (parts.length != 3) {
                return null;
            }
            long hours = Long.parseLong(parts[0].trim());
            long minutes = Long.parseLong(parts[1].trim());
            long seconds = Long.parseLong(parts[2].trim());
            return Duration.ofHours(hours).plusMinutes(minutes).plusSeconds(seconds);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Duration parsePace(String formatted) {
        if (formatted == null || formatted.isBlank()) {
            return null;
        }
        String value = formatted.endsWith("/km") ? formatted.substring(0, formatted.length() - 3) : formatted;
        String[] parts = value.split(":");
        try {
            if (parts.length != 2) {
                return null;
            }
            long minutes = Long.parseLong(parts[0].trim());
            long seconds = Long.parseLong(parts[1].trim());
            return Duration.ofMinutes(minutes).plusSeconds(seconds);
        } catch (NumberFormatException ex) {
            return null;
        }
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

}
