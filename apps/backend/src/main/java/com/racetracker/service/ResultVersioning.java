package com.racetracker.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.racetracker.model.RaceResult;
import com.racetracker.model.RaceResultVersion;
import com.racetracker.repository.RaceResultVersionRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Tamper-evident result releases. Every publish stores an immutable copy of the results (result
 * fields keyed by participant id, no personal data) with a SHA-256 of its canonical form, chained
 * to the previous release. Re-hashing the live results table or the stored copies detects any
 * change made outside the application.
 */
@Component
public class ResultVersioning {

    public record ResultLine(
        String participantId,
        String categoryId,
        String gunTime,
        String chipTime,
        Integer overallPosition,
        Integer genderPosition,
        Integer categoryPosition,
        String ageGroup,
        Integer ageGroupPosition,
        Integer penaltySeconds,
        String averagePace,
        String averageSpeed,
        boolean official,
        boolean disqualified,
        String disqualificationReason
    ) {
    }

    private static final TypeReference<List<ResultLine>> LINES = new TypeReference<>() { };

    private final RaceResultVersionRepository versionRepository;
    private final ObjectMapper objectMapper;

    public ResultVersioning(RaceResultVersionRepository versionRepository, ObjectMapper objectMapper) {
        this.versionRepository = versionRepository;
        this.objectMapper = objectMapper;
    }

    public RaceResultVersion record(UUID raceId, List<RaceResult> rows, UUID publishedBy) {
        List<ResultLine> lines = lines(rows);
        String contentHash = contentHash(lines);
        RaceResultVersion previous = versionRepository.findTopByRaceIdOrderByVersionDesc(raceId).orElse(null);
        int nextVersion = previous == null ? 1 : previous.getVersion() + 1;
        String previousChain = previous == null ? null : previous.getChainSha256();
        if (previous != null && previous.getStatus() != RaceResultVersion.Status.SUPERSEDED) {
            previous.setStatus(RaceResultVersion.Status.SUPERSEDED);
            versionRepository.save(previous);
        }

        RaceResultVersion version = new RaceResultVersion();
        version.setRaceId(raceId);
        version.setVersion(nextVersion);
        version.setStatus(RaceResultVersion.Status.PROVISIONAL);
        version.setResultCount(lines.size());
        version.setContentSha256(contentHash);
        version.setPreviousChainSha256(previousChain);
        version.setChainSha256(chainHash(previousChain, contentHash, nextVersion, raceId));
        version.setSnapshot(toJson(lines));
        version.setPublishedBy(publishedBy);
        version.setPublishedAt(OffsetDateTime.now());
        return versionRepository.save(version);
    }

    public String contentHash(List<ResultLine> lines) {
        StringBuilder canonical = new StringBuilder();
        for (ResultLine line : lines) {
            canonical.append(String.join("|",
                text(line.participantId()), text(line.categoryId()), text(line.gunTime()), text(line.chipTime()),
                text(line.overallPosition()), text(line.genderPosition()), text(line.categoryPosition()),
                text(line.ageGroup()), text(line.ageGroupPosition()), text(line.penaltySeconds()),
                text(line.averagePace()), text(line.averageSpeed()), Boolean.toString(line.official()),
                Boolean.toString(line.disqualified()), text(line.disqualificationReason())))
                .append('\n');
        }
        return sha256(canonical.toString());
    }

    public List<ResultLine> lines(List<RaceResult> rows) {
        return rows.stream()
            .map(row -> new ResultLine(
                row.getParticipant() == null ? null : row.getParticipant().getId().toString(),
                row.getCategory() == null ? null : row.getCategory().getId().toString(),
                duration(row.getGunTime()),
                duration(row.getChipTime()),
                row.getOverallPosition(),
                row.getGenderPosition(),
                row.getCategoryPosition(),
                row.getAgeGroup(),
                row.getAgeGroupPosition(),
                row.getPenaltySeconds(),
                duration(row.getAveragePace()),
                decimal(row.getAverageSpeed()),
                Boolean.TRUE.equals(row.getIsOfficial()),
                Boolean.TRUE.equals(row.getIsDisqualified()),
                row.getDisqualificationReason()))
            .sorted(Comparator.comparing(line -> line.participantId() == null ? "" : line.participantId()))
            .toList();
    }

    public List<ResultLine> snapshotLines(RaceResultVersion version) {
        try {
            return objectMapper.readValue(version.getSnapshot(), LINES).stream()
                .sorted(Comparator.comparing(line -> line.participantId() == null ? "" : line.participantId()))
                .toList();
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    public static String chainHash(String previousChain, String contentHash, int version, UUID raceId) {
        return sha256((previousChain == null ? "" : previousChain) + "|" + contentHash + "|" + version + "|" + raceId);
    }

    private String toJson(List<ResultLine> lines) {
        try {
            return objectMapper.writeValueAsString(lines);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Result snapshot could not be serialised", ex);
        }
    }

    private static String duration(Duration value) {
        return value == null ? null : value.toString();
    }

    private static String decimal(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString().replace("|", "\\|").replace("\n", " ");
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
