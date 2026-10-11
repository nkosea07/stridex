package com.racetracker.service.impl;

import com.opencsv.CSVReader;
import com.racetracker.model.ImportJob;
import com.racetracker.model.Participant;
import com.racetracker.model.Race;
import com.racetracker.model.RaceCategory;
import com.racetracker.repository.ImportJobRepository;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.RaceCategoryRepository;
import com.racetracker.service.ParticipantCredentialService;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Processes CSV/Excel participant imports on a background worker. Jobs are persisted with their
 * source file so a scheduled recovery can resume or retry work that did not finish.
 */
@Slf4j
@Component
public class ParticipantImportProcessor {

    private static final int MAX_ATTEMPTS = 3;
    private static final Duration STALE_THRESHOLD = Duration.ofMinutes(10);

    private final ImportJobRepository importJobRepository;
    private final ParticipantRepository participantRepository;
    private final ParticipantCredentialService participantCredentialService;
    private final RaceCategoryRepository raceCategoryRepository;

    public ParticipantImportProcessor(
        ImportJobRepository importJobRepository,
        ParticipantRepository participantRepository,
        ParticipantCredentialService participantCredentialService,
        RaceCategoryRepository raceCategoryRepository
    ) {
        this.importJobRepository = importJobRepository;
        this.participantRepository = participantRepository;
        this.participantCredentialService = participantCredentialService;
        this.raceCategoryRepository = raceCategoryRepository;
    }

    @Async
    public void process(UUID jobId) {
        runJob(jobId);
    }

    @Scheduled(
        fixedDelayString = "${racetracker.imports.recovery-fixed-delay-millis:30000}",
        initialDelayString = "${racetracker.imports.recovery-initial-delay-millis:20000}"
    )
    public void recoverStuckJobs() {
        List<ImportJob> candidates = importJobRepository.findByStatusIn(
            List.of(ImportJob.JobStatus.PENDING, ImportJob.JobStatus.PROCESSING));
        OffsetDateTime cutoff = OffsetDateTime.now().minus(STALE_THRESHOLD);
        for (ImportJob job : candidates) {
            if (job.getSourcePath() == null || job.getSourcePath().isBlank()) {
                continue;
            }
            if (job.getStatus() == ImportJob.JobStatus.PROCESSING
                && job.getUpdatedAt() != null && job.getUpdatedAt().isAfter(cutoff)) {
                continue;
            }
            log.info("Recovering import job {}", job.getId());
            runJob(job.getId());
        }
    }

    void runJob(UUID jobId) {
        ImportJob job = importJobRepository.findWithRaceById(jobId).orElse(null);
        if (job == null || job.getSourcePath() == null || job.getSourcePath().isBlank()) {
            return;
        }
        if (job.getStatus() == ImportJob.JobStatus.COMPLETED) {
            return;
        }

        int attempts = job.getAttempts() == null ? 0 : job.getAttempts();
        job.setAttempts(attempts + 1);
        job.setStatus(ImportJob.JobStatus.PROCESSING);
        job.setStartedAt(offsetOrNow(job.getStartedAt()));
        importJobRepository.save(job);

        int imported = 0;
        int skipped = 0;
        int total = 0;
        try {
            Race race = job.getRace();
            Set<String> seenEmails = new HashSet<>(participantRepository.findEmailsByRaceId(race.getId()));
            List<String[]> rows = readImportRows(job.getSourcePath());
            if (!rows.isEmpty()) {
                Map<String, Integer> index = headerIndex(rows.get(0));
                for (int rowIndex = 1; rowIndex < rows.size(); rowIndex++) {
                    String[] row = rows.get(rowIndex);
                    total++;
                    try {
                        String email = requiredCell(row, index, "email").toLowerCase(Locale.ROOT);
                        if (!seenEmails.add(email)) {
                            skipped++;
                            continue;
                        }
                        Participant participant = new Participant();
                        participant.setRace(race);
                        participant.setFirstName(requiredCell(row, index, "firstName"));
                        participant.setLastName(requiredCell(row, index, "lastName"));
                        participant.setEmail(email);
                        participant.setPhone(cell(row, index, "phone"));
                        participant.setNationality(cell(row, index, "nationality"));
                        participant.setBibNumber(cell(row, index, "bibNumber"));
                        participant.setRfidTag(cell(row, index, "rfidTag"));
                        participant.setRegistrationSource(
                            Optional.ofNullable(cell(row, index, "registrationSource")).orElse("CSV_IMPORT"));
                        participant.setStatus(parseStatusNullable(cell(row, index, "status"),
                            Participant.ParticipantStatus.REGISTERED));
                        participant.setGender(parseGenderNullable(cell(row, index, "gender")));
                        participant.setIsTrackingEnabled(parseBoolean(cell(row, index, "isTrackingEnabled"), true));
                        participant.setPaymentStatus(defaultPaymentStatus(race));

                        String dob = cell(row, index, "dateOfBirth");
                        if (dob != null && !dob.isBlank()) {
                            participant.setDateOfBirth(LocalDate.parse(dob));
                        }
                        String categoryId = cell(row, index, "categoryId");
                        if (categoryId != null && !categoryId.isBlank()) {
                            participant.setCategory(resolveCategory(race.getId(), UUID.fromString(categoryId)));
                        }
                        participant.setRegisteredAt(OffsetDateTime.now());
                        Participant saved = participantRepository.save(participant);
                        participantCredentialService.syncParticipantCredentials(saved, cell(row, index, "rfidTag"), null);
                        imported++;
                    } catch (Exception rowError) {
                        skipped++;
                    }
                    if (total % 10 == 0) {
                        job.setProcessedRows(total);
                        job.setSuccessCount(imported);
                        job.setErrorCount(skipped);
                        importJobRepository.save(job);
                    }
                }
            }
            job.setStatus(ImportJob.JobStatus.COMPLETED);
        } catch (Exception ex) {
            if (job.getAttempts() >= MAX_ATTEMPTS) {
                job.setStatus(ImportJob.JobStatus.FAILED);
                job.setErrorMessage(ex.getMessage());
            } else {
                job.setStatus(ImportJob.JobStatus.PENDING);
                job.setErrorMessage("Retrying after error: " + ex.getMessage());
            }
            log.warn("Import job {} failed on attempt {}: {}", jobId, job.getAttempts(), ex.getMessage());
        } finally {
            job.setTotalRows(total);
            job.setProcessedRows(total);
            job.setSuccessCount(imported);
            job.setErrorCount(skipped);
            if (job.getStatus() == ImportJob.JobStatus.COMPLETED || job.getStatus() == ImportJob.JobStatus.FAILED) {
                job.setCompletedAt(OffsetDateTime.now());
            }
            importJobRepository.save(job);

            if (job.getStatus() == ImportJob.JobStatus.COMPLETED || job.getStatus() == ImportJob.JobStatus.FAILED) {
                try {
                    Files.deleteIfExists(Paths.get(job.getSourcePath()));
                } catch (IOException ignored) {
                    // best-effort cleanup
                }
            }
        }
    }

    private OffsetDateTime offsetOrNow(OffsetDateTime value) {
        return value == null ? OffsetDateTime.now() : value;
    }

    private List<String[]> readImportRows(String filePath) throws IOException {
        String lower = filePath.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
            return readExcelRows(filePath);
        }
        try (CSVReader reader = new CSVReader(
            new InputStreamReader(new FileInputStream(filePath), StandardCharsets.UTF_8))) {
            return reader.readAll();
        } catch (com.opencsv.exceptions.CsvException ex) {
            throw new IOException("Failed to parse CSV import file", ex);
        }
    }

    private List<String[]> readExcelRows(String filePath) throws IOException {
        List<String[]> rows = new ArrayList<>();
        try (InputStream input = new FileInputStream(filePath);
             Workbook workbook = WorkbookFactory.create(input)) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            for (Row row : sheet) {
                int lastCell = row.getLastCellNum();
                if (lastCell <= 0) {
                    rows.add(new String[0]);
                    continue;
                }
                String[] values = new String[lastCell];
                for (int column = 0; column < lastCell; column++) {
                    values[column] = formatter.formatCellValue(row.getCell(column));
                }
                rows.add(values);
            }
        }
        return rows;
    }

    private Map<String, Integer> headerIndex(String[] headers) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            if (headers[i] != null) {
                map.put(headers[i].trim(), i);
            }
        }
        return map;
    }

    private String cell(String[] row, Map<String, Integer> index, String key) {
        Integer i = index.get(key);
        if (i == null || i < 0 || i >= row.length) return null;
        String value = row[i];
        return value == null ? null : value.trim();
    }

    private String requiredCell(String[] row, Map<String, Integer> index, String key) {
        String value = cell(row, index, key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required field: " + key);
        }
        return value;
    }

    private Participant.ParticipantStatus parseStatusNullable(String input, Participant.ParticipantStatus fallback) {
        if (input == null || input.isBlank()) {
            return fallback;
        }
        return Participant.ParticipantStatus.valueOf(input.trim().toUpperCase(Locale.ROOT));
    }

    private Participant.Gender parseGenderNullable(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        return Participant.Gender.valueOf(input.trim().toUpperCase(Locale.ROOT));
    }

    private boolean parseBoolean(String input, boolean fallback) {
        if (input == null || input.isBlank()) return fallback;
        return "true".equalsIgnoreCase(input) || "1".equals(input) || "yes".equalsIgnoreCase(input);
    }

    private Participant.PaymentStatus defaultPaymentStatus(Race race) {
        if (race.getRegistrationFee() == null || race.getRegistrationFee().compareTo(BigDecimal.ZERO) <= 0) {
            return Participant.PaymentStatus.WAIVED;
        }
        return Participant.PaymentStatus.UNPAID;
    }

    private RaceCategory resolveCategory(UUID raceId, UUID categoryId) {
        RaceCategory category = raceCategoryRepository.findById(categoryId)
            .orElseThrow(() -> new IllegalArgumentException("Race category not found"));
        if (category.getRace() == null || !raceId.equals(category.getRace().getId())) {
            throw new IllegalArgumentException("Category does not belong to race");
        }
        return category;
    }
}
