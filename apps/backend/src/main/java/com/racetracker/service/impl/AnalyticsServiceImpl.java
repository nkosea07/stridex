package com.racetracker.service.impl;

import com.racetracker.dto.analytics.DemographicsDto;
import com.racetracker.dto.analytics.RaceAnalyticsSummaryDto;
import com.racetracker.dto.analytics.RegistrationPointDto;
import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.model.Participant;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.RaceRepository;
import com.racetracker.service.AnalyticsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class AnalyticsServiceImpl implements AnalyticsService {

    private final ParticipantRepository participantRepository;
    private final RaceRepository raceRepository;

    public AnalyticsServiceImpl(ParticipantRepository participantRepository, RaceRepository raceRepository) {
        this.participantRepository = participantRepository;
        this.raceRepository = raceRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public RaceAnalyticsSummaryDto summary(UUID raceId) {
        requireRace(raceId);

        RaceAnalyticsSummaryDto dto = new RaceAnalyticsSummaryDto();
        dto.setTotalRegistrations(participantRepository.countByRaceId(raceId));

        for (Object[] row : participantRepository.countByStatusGrouped(raceId)) {
            Participant.ParticipantStatus status = (Participant.ParticipantStatus) row[0];
            long count = ((Number) row[1]).longValue();
            dto.getStatusCounts().put(status == null ? "UNKNOWN" : status.name(), count);
        }

        BigDecimal revenue = BigDecimal.ZERO;
        for (Object[] row : participantRepository.paymentTotals(raceId)) {
            Participant.PaymentStatus status = (Participant.PaymentStatus) row[0];
            long count = ((Number) row[1]).longValue();
            BigDecimal sum = row[2] == null ? BigDecimal.ZERO : new BigDecimal(row[2].toString());
            switch (status) {
                case PAID -> {
                    dto.setPaidCount(count);
                    revenue = revenue.add(sum);
                }
                case UNPAID, PENDING -> dto.setUnpaidCount(dto.getUnpaidCount() + count);
                case REFUNDED -> dto.setRefundedCount(count);
                default -> { }
            }
        }
        dto.setRevenue(revenue);

        long finished = dto.getStatusCounts().getOrDefault("FINISHED", 0L);
        dto.setCompletionRate(dto.getTotalRegistrations() == 0 ? 0.0
            : Math.round((finished * 1000.0) / dto.getTotalRegistrations()) / 10.0);

        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    public DemographicsDto demographics(UUID raceId) {
        requireRace(raceId);
        DemographicsDto dto = new DemographicsDto();
        put(dto.getGender(), participantRepository.countByGender(raceId));
        put(dto.getCountries(), participantRepository.countByNationality(raceId));
        put(dto.getShirtSizes(), participantRepository.countByShirtSize(raceId));
        put(dto.getAgeGroups(), participantRepository.countByAgeGroup(raceId));
        return dto;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RegistrationPointDto> registrationSeries(UUID raceId, int days) {
        requireRace(raceId);
        int window = Math.min(Math.max(days, 1), 365);
        return participantRepository.registrationSeries(raceId, window).stream()
            .map(row -> new RegistrationPointDto(String.valueOf(row[0]), ((Number) row[1]).longValue()))
            .toList();
    }

    private void put(java.util.Map<String, Long> target, List<Object[]> rows) {
        for (Object[] row : rows) {
            String key = row[0] == null ? "UNKNOWN" : row[0].toString();
            target.put(key, ((Number) row[1]).longValue());
        }
    }

    private void requireRace(UUID raceId) {
        if (!raceRepository.existsById(raceId)) {
            throw new ResourceNotFoundException("Race not found");
        }
    }
}
