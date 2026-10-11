package com.racetracker.service.impl;

import com.racetracker.dto.analytics.DemographicsDto;
import com.racetracker.dto.analytics.RaceAnalyticsSummaryDto;
import com.racetracker.dto.analytics.RegistrationPointDto;
import com.racetracker.exception.ApiException;
import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.model.Race;
import com.racetracker.repository.RaceRepository;
import com.racetracker.service.AnalyticsExportService;
import com.racetracker.service.AnalyticsService;
import com.racetracker.service.ExportedFile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class AnalyticsExportServiceImpl implements AnalyticsExportService {

    private static final int REGISTRATION_WINDOW_DAYS = 90;

    private final AnalyticsService analyticsService;
    private final RaceRepository raceRepository;

    public AnalyticsExportServiceImpl(AnalyticsService analyticsService, RaceRepository raceRepository) {
        this.analyticsService = analyticsService;
        this.raceRepository = raceRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public ExportedFile export(UUID raceId, String format) {
        Race race = raceRepository.findById(raceId)
            .orElseThrow(() -> new ResourceNotFoundException("Race not found"));

        RaceAnalyticsSummaryDto summary = analyticsService.summary(raceId);
        DemographicsDto demographics = analyticsService.demographics(raceId);
        List<RegistrationPointDto> registrations = analyticsService.registrationSeries(raceId, REGISTRATION_WINDOW_DAYS);

        String safeName = (race.getName() == null ? "race" : race.getName())
            .replaceAll("[^a-zA-Z0-9-_]+", "-")
            .toLowerCase(Locale.ROOT);
        String normalized = format == null ? "csv" : format.trim().toLowerCase(Locale.ROOT);

        return switch (normalized) {
            case "csv" -> new ExportedFile(TabularExportWriter.csv(toCsvRows(summary, demographics, registrations)),
                "text/csv", safeName + "-analytics.csv");
            case "xlsx" -> new ExportedFile(TabularExportWriter.xlsx(toSheets(summary, demographics, registrations)),
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", safeName + "-analytics.xlsx");
            default -> throw new ApiException("Unsupported export format: " + format + " (use csv or xlsx)");
        };
    }

    private Map<String, List<String[]>> toSheets(RaceAnalyticsSummaryDto summary,
                                                 DemographicsDto demographics,
                                                 List<RegistrationPointDto> registrations) {
        Map<String, List<String[]>> sheets = new LinkedHashMap<>();
        sheets.put("Summary", summaryRows(summary));
        sheets.put("Status Counts", mapRows("Status", "Count", summary.getStatusCounts()));
        sheets.put("Registrations", registrationRows(registrations));
        sheets.put("Demographics", demographicsRows(demographics));
        return sheets;
    }

    private List<String[]> toCsvRows(RaceAnalyticsSummaryDto summary,
                                     DemographicsDto demographics,
                                     List<RegistrationPointDto> registrations) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {"Summary"});
        rows.addAll(summaryRows(summary));
        rows.add(new String[] {""});
        rows.add(new String[] {"Status Counts"});
        rows.add(new String[] {"Status", "Count"});
        summary.getStatusCounts().forEach((key, value) -> rows.add(new String[] {key, String.valueOf(value)}));
        rows.add(new String[] {""});
        rows.add(new String[] {"Registrations"});
        rows.add(new String[] {"Date", "Count"});
        rows.addAll(registrationRows(registrations));
        rows.add(new String[] {""});
        rows.add(new String[] {"Demographics"});
        rows.add(new String[] {"Dimension", "Value", "Count"});
        demographicsRows(demographics).stream().skip(1).forEach(rows::add);
        return rows;
    }

    private List<String[]> summaryRows(RaceAnalyticsSummaryDto summary) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {"Metric", "Value"});
        rows.add(new String[] {"Total Registrations", String.valueOf(summary.getTotalRegistrations())});
        rows.add(new String[] {"Paid", String.valueOf(summary.getPaidCount())});
        rows.add(new String[] {"Unpaid", String.valueOf(summary.getUnpaidCount())});
        rows.add(new String[] {"Refunded", String.valueOf(summary.getRefundedCount())});
        rows.add(new String[] {"Revenue", summary.getRevenue() == null ? "0" : summary.getRevenue().toPlainString()});
        rows.add(new String[] {"Completion Rate (%)", String.valueOf(summary.getCompletionRate())});
        return rows;
    }

    private List<String[]> mapRows(String keyHeader, String valueHeader, Map<String, Long> values) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {keyHeader, valueHeader});
        values.forEach((key, value) -> rows.add(new String[] {key, String.valueOf(value)}));
        return rows;
    }

    private List<String[]> registrationRows(List<RegistrationPointDto> registrations) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {"Date", "Count"});
        for (RegistrationPointDto point : registrations) {
            rows.add(new String[] {point.getDate(), String.valueOf(point.getCount())});
        }
        return rows;
    }

    private List<String[]> demographicsRows(DemographicsDto demographics) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {"Dimension", "Value", "Count"});
        addDimension(rows, "Gender", demographics.getGender());
        addDimension(rows, "Age Group", demographics.getAgeGroups());
        addDimension(rows, "Country", demographics.getCountries());
        addDimension(rows, "Shirt Size", demographics.getShirtSizes());
        return rows;
    }

    private void addDimension(List<String[]> rows, String dimension, Map<String, Long> values) {
        values.forEach((key, value) -> rows.add(new String[] {dimension, key, String.valueOf(value)}));
    }
}
