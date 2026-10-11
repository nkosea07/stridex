package com.racetracker.service;

import com.racetracker.dto.analytics.DemographicsDto;
import com.racetracker.dto.analytics.RaceAnalyticsSummaryDto;
import com.racetracker.dto.analytics.RegistrationPointDto;
import com.racetracker.model.Participant;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.RaceRepository;
import com.racetracker.service.impl.AnalyticsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceImplTest {

    @Mock ParticipantRepository participantRepository;
    @Mock RaceRepository raceRepository;

    private AnalyticsServiceImpl service;
    private final UUID raceId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AnalyticsServiceImpl(participantRepository, raceRepository);
        when(raceRepository.existsById(raceId)).thenReturn(true);
    }

    @Test
    void summaryAggregatesStatusesRevenueAndCompletion() {
        when(participantRepository.countByRaceId(raceId)).thenReturn(10L);
        when(participantRepository.countByStatusGrouped(raceId)).thenReturn(List.<Object[]>of(
            new Object[]{Participant.ParticipantStatus.FINISHED, 6L},
            new Object[]{Participant.ParticipantStatus.DNF, 2L}
        ));
        when(participantRepository.paymentTotals(raceId)).thenReturn(List.<Object[]>of(
            new Object[]{Participant.PaymentStatus.PAID, 6L, new BigDecimal("600.00")},
            new Object[]{Participant.PaymentStatus.UNPAID, 4L, BigDecimal.ZERO}
        ));

        RaceAnalyticsSummaryDto summary = service.summary(raceId);

        assertThat(summary.getTotalRegistrations()).isEqualTo(10);
        assertThat(summary.getPaidCount()).isEqualTo(6);
        assertThat(summary.getUnpaidCount()).isEqualTo(4);
        assertThat(summary.getRevenue()).isEqualByComparingTo("600.00");
        assertThat(summary.getCompletionRate()).isEqualTo(60.0);
        assertThat(summary.getStatusCounts()).containsEntry("FINISHED", 6L);
    }

    @Test
    void demographicsMapsDimensionCounts() {
        when(participantRepository.countByGender(raceId)).thenReturn(List.<Object[]>of(new Object[]{Participant.Gender.MALE, 7L}));
        when(participantRepository.countByNationality(raceId)).thenReturn(List.<Object[]>of(new Object[]{"ZIM", 10L}));
        when(participantRepository.countByShirtSize(raceId)).thenReturn(List.<Object[]>of(new Object[]{"M", 5L}));
        when(participantRepository.countByAgeGroup(raceId)).thenReturn(List.<Object[]>of(new Object[]{"30-39", 4L}));

        DemographicsDto dto = service.demographics(raceId);

        assertThat(dto.getGender()).containsEntry("MALE", 7L);
        assertThat(dto.getCountries()).containsEntry("ZIM", 10L);
        assertThat(dto.getShirtSizes()).containsEntry("M", 5L);
        assertThat(dto.getAgeGroups()).containsEntry("30-39", 4L);
    }

    @Test
    void registrationSeriesMapsPoints() {
        when(participantRepository.registrationSeries(raceId, 30)).thenReturn(List.<Object[]>of(new Object[]{"2026-10-01", 3L}));

        List<RegistrationPointDto> series = service.registrationSeries(raceId, 30);

        assertThat(series).hasSize(1);
        assertThat(series.get(0).getDate()).isEqualTo("2026-10-01");
        assertThat(series.get(0).getCount()).isEqualTo(3);
    }
}
