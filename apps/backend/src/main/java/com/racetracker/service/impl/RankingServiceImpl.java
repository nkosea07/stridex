package com.racetracker.service.impl;

import com.racetracker.dto.result.ParticipantResultDto;
import com.racetracker.dto.result.RaceResultDto;
import com.racetracker.dto.result.RankingEntryDto;
import com.racetracker.service.RankingService;
import com.racetracker.service.ResultService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class RankingServiceImpl implements RankingService {

    private final ResultService resultService;

    public RankingServiceImpl(ResultService resultService) {
        this.resultService = resultService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RankingEntryDto> teamRankings(UUID raceId) {
        return aggregate(raceId, result -> {
            ParticipantResultDto participant = result.getParticipant();
            if (participant == null) {
                return null;
            }
            String name = firstNonBlank(participant.getTeamName(), participant.getClubName());
            return name == null ? null : name.trim();
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<RankingEntryDto> countryRankings(UUID raceId) {
        return aggregate(raceId, result -> {
            ParticipantResultDto participant = result.getParticipant();
            if (participant == null || participant.getNationality() == null || participant.getNationality().isBlank()) {
                return null;
            }
            return participant.getNationality().trim().toUpperCase(java.util.Locale.ROOT);
        });
    }

    private List<RankingEntryDto> aggregate(UUID raceId, java.util.function.Function<RaceResultDto, String> keyFn) {
        Map<String, long[]> totals = new LinkedHashMap<>();
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, Long> best = new LinkedHashMap<>();

        for (RaceResultDto result : resultService.listByRace(raceId)) {
            if (!result.isOfficial() || result.isDisqualified()) {
                continue;
            }
            Long seconds = parseSeconds(result.getTotalTime());
            if (seconds == null) {
                continue;
            }
            String key = keyFn.apply(result);
            if (key == null || key.isBlank()) {
                continue;
            }
            totals.computeIfAbsent(key, k -> new long[1])[0] += seconds;
            counts.merge(key, 1, Integer::sum);
            best.merge(key, seconds, Math::min);
        }

        List<RankingEntryDto> entries = new ArrayList<>();
        for (Map.Entry<String, long[]> entry : totals.entrySet()) {
            String key = entry.getKey();
            long total = entry.getValue()[0];
            int count = counts.get(key);
            RankingEntryDto dto = new RankingEntryDto();
            dto.setName(key);
            dto.setFinishers(count);
            dto.setTotalSeconds(total);
            dto.setTotalTime(format(total));
            dto.setAverageTime(format(count == 0 ? 0 : total / count));
            dto.setBestTime(format(best.get(key)));
            entries.add(dto);
        }
        entries.sort(Comparator.comparingLong(RankingEntryDto::getTotalSeconds)
            .thenComparing(RankingEntryDto::getName));
        return entries;
    }

    private Long parseSeconds(String totalTime) {
        if (totalTime == null || totalTime.isBlank()) {
            return null;
        }
        String[] parts = totalTime.split(":");
        try {
            if (parts.length == 3) {
                return Long.parseLong(parts[0]) * 3600 + Long.parseLong(parts[1]) * 60 + Long.parseLong(parts[2]);
            }
            if (parts.length == 2) {
                return Long.parseLong(parts[0]) * 60 + Long.parseLong(parts[1]);
            }
        } catch (NumberFormatException ex) {
            return null;
        }
        return null;
    }

    private String format(Long seconds) {
        if (seconds == null) {
            return null;
        }
        long total = seconds;
        return String.format("%02d:%02d:%02d", total / 3600, (total % 3600) / 60, total % 60);
    }

    private String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b != null && !b.isBlank() ? b : null;
    }
}
