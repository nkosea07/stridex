package com.racetracker.service;

import com.racetracker.dto.analytics.DemographicsDto;
import com.racetracker.dto.analytics.RaceAnalyticsSummaryDto;
import com.racetracker.dto.analytics.RegistrationPointDto;
import com.racetracker.model.Race;
import com.racetracker.repository.RaceRepository;
import com.racetracker.service.impl.AnalyticsExportServiceImpl;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalyticsExportServiceImplTest {

    private AnalyticsExportServiceImpl service(UUID raceId, RaceAnalyticsSummaryDto summary,
                                               DemographicsDto demographics, List<RegistrationPointDto> registrations) {
        Race race = new Race();
        race.setId(raceId);
        race.setName("Demo Marathon");
        AnalyticsService analyticsService = mock(AnalyticsService.class);
        RaceRepository raceRepository = mock(RaceRepository.class);
        when(raceRepository.findById(raceId)).thenReturn(Optional.of(race));
        when(analyticsService.summary(raceId)).thenReturn(summary);
        when(analyticsService.demographics(raceId)).thenReturn(demographics);
        when(analyticsService.registrationSeries(org.mockito.ArgumentMatchers.eq(raceId), anyInt())).thenReturn(registrations);
        return new AnalyticsExportServiceImpl(analyticsService, raceRepository);
    }

    @Test
    void csvExportIncludesSummaryAndDemographics() {
        UUID raceId = UUID.randomUUID();
        RaceAnalyticsSummaryDto summary = new RaceAnalyticsSummaryDto();
        summary.setTotalRegistrations(120);
        summary.setPaidCount(100);
        summary.setRevenue(BigDecimal.valueOf(5400));
        summary.getStatusCounts().put("FINISHED", 80L);

        DemographicsDto demographics = new DemographicsDto();
        demographics.getGender().put("FEMALE", 60L);

        ExportedFile file = service(raceId, summary, demographics,
            List.of(new RegistrationPointDto("2026-05-01", 5)))
            .export(raceId, "csv");

        String csv = new String(file.content(), StandardCharsets.UTF_8);
        assertThat(csv).contains("Summary");
        assertThat(csv).contains("\"Total Registrations\",\"120\"");
        assertThat(csv).contains("FEMALE");
        assertThat(csv).contains("\"2026-05-01\",\"5\"");
    }

    @Test
    void xlsxExportProducesWorkbookBytes() {
        UUID raceId = UUID.randomUUID();
        ExportedFile file = service(raceId, new RaceAnalyticsSummaryDto(), new DemographicsDto(), List.of())
            .export(raceId, "xlsx");

        assertThat(file.content()).isNotEmpty();
        assertThat(file.filename()).endsWith(".xlsx");
    }
}
