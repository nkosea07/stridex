package com.racetracker.service.impl;

import com.opencsv.CSVReader;
import com.racetracker.exception.ConflictException;
import com.racetracker.model.ImportJob;
import com.racetracker.model.Participant;
import com.racetracker.model.Race;
import com.racetracker.model.RaceCategory;
import com.racetracker.repository.ImportJobRepository;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.RaceCategoryRepository;
import jakarta.persistence.PersistenceException;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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

    private static final Duration STALE_THRESHOLD = Duration.ofMinutes(10);
    private static final java.util.regex.Pattern EMAIL =
        java.util.regex.Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final ImportJobRepository importJobRepository;
    private final ParticipantRepository participantRepository;
    private final RaceCategoryRepository raceCategoryRepository;
    private final ParticipantImportRowService rowService;
    private final ParticipantImportJobService jobService;
    private final ImportSourceStore sourceStore;

    public ParticipantImportProcessor(
        ImportJobRepository importJobRepository,
        ParticipantRepository participantRepository,
        RaceCategoryRepository raceCategoryRepository,
        ParticipantImportRowService rowService,
        ParticipantImportJobService jobService,
        ImportSourceStore sourceStore
    ) {
        this.importJobRepository = importJobRepository;
        this.participantRepository = participantRepository;
        this.raceCategoryRepository = raceCategoryRepository;
        this.rowService = rowService;
        this.jobService = jobService;
        this.sourceStore = sourceStore;
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
        List<ImportJob> candidates;
        try {
            candidates = importJobRepository.findByStatusIn(
                List.of(ImportJob.JobStatus.PENDING, ImportJob.JobStatus.PROCESSING));
        } catch (DataAccessException | TransactionException | PersistenceException ex) {
            log.warn("Unable to scan participant import jobs; scheduled recovery will retry", ex);
            return;
        }
        OffsetDateTime cutoff = OffsetDateTime.now().minus(STALE_THRESHOLD);
        for (ImportJob job : candidates) {
            if (!sourceStore.hasSource(job)) {
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

    public com.racetracker.dto.participant.ImportPreviewDto preview(java.io.InputStream input,
                                                                     String fileName, UUID raceId) {
        try {
            List<String[]> rows = readImportRows(input, fileName);
            if (rows.isEmpty()) {
                return new com.racetracker.dto.participant.ImportPreviewDto(0, 0, 0, List.of());
            }
            Map<String, Integer> index = headerIndex(rows.get(0));
            Race previewRace = new Race();
            previewRace.setId(raceId);
            Set<String> seenEmails = new HashSet<>(participantRepository.findEmailsByRaceId(raceId));
            Set<String> seenBibs = new HashSet<>();
            Set<String> seenTags = new HashSet<>();
            List<com.racetracker.dto.participant.ImportPreviewDto.Row> previewRows = new ArrayList<>();
            int valid = 0;
            int duplicates = 0;
            int dataRows = 0;
            for (int rowIndex = 1; rowIndex < rows.size(); rowIndex++) {
                String[] row = rows.get(rowIndex);
                if (isBlankRow(row)) {
                    continue;
                }
                dataRows++;
                List<String> errors = new ArrayList<>();
                Map<String, String> values = new java.util.LinkedHashMap<>();
                for (String key : List.of("firstName", "lastName", "email", "phone", "bibNumber",
                    "categoryId", "dateOfBirth", "gender", "nationality", "rfidTag")) {
                    values.put(key, cell(row, index, key));
                }
                Participant parsed = null;
                try {
                    parsed = parseParticipant(row, index, previewRace);
                } catch (IllegalArgumentException invalidRow) {
                    errors.add(invalidRow.getMessage());
                }
                if (parsed != null) {
                    String duplicate = duplicateReason(parsed, seenEmails, seenBibs, seenTags);
                    if (duplicate != null) {
                        duplicates++;
                        errors.add(duplicate);
                    }
                }
                if (errors.isEmpty()) {
                    valid++;
                }
                if (previewRows.size() < 200) {
                    previewRows.add(new com.racetracker.dto.participant.ImportPreviewDto.Row(
                        rowIndex, values, errors));
                }
            }
            return new com.racetracker.dto.participant.ImportPreviewDto(dataRows, valid, duplicates, previewRows);
        } catch (IOException ex) {
            throw new com.racetracker.exception.ApiException("Could not read import file", "IMPORT_FILE_UNREADABLE");
        }
    }

    private String duplicateReason(Participant parsed, Set<String> seenEmails, Set<String> seenBibs, Set<String> seenTags) {
        UUID raceId = parsed.getRace().getId();
        if (!seenEmails.add(parsed.getEmail())) {
            return "Duplicate email";
        }
        if (parsed.getBibNumber() != null
            && (!seenBibs.add(parsed.getBibNumber()) || participantRepository.existsByRaceIdAndBibNumber(raceId, parsed.getBibNumber()))) {
            return "Duplicate bibNumber";
        }
        if (parsed.getRfidTag() != null
            && (!seenTags.add(parsed.getRfidTag()) || participantRepository.existsByRaceIdAndRfidTag(raceId, parsed.getRfidTag()))) {
            return "Duplicate rfidTag";
        }
        return null;
    }

    private boolean isBlankRow(String[] row) {
        if (row == null) {
            return true;
        }
        for (String value : row) {
            if (value != null && !value.isBlank()) {
                return false;
            }
        }
        return true;
    }

    void runJob(UUID jobId) {
        Optional<ParticipantImportJobService.Claim> claimed;
        try {
            claimed = jobService.claim(jobId, OffsetDateTime.now().minus(STALE_THRESHOLD));
        } catch (DataAccessException | TransactionException | PersistenceException ex) {
            log.warn("Unable to claim participant import job {}; persisted state is uncertain; "
                + "scheduled recovery will recheck", jobId, ex);
            return;
        }
        if (claimed.isEmpty()) {
            return;
        }
        ImportJob job = claimed.get().job();
        int attempt = claimed.get().attempt();

        try {
            Race race = job.getRace();
            Optional<byte[]> storedSource = sourceStore.read(jobId);
            List<String[]> rows = storedSource.isPresent()
                ? readImportRows(new java.io.ByteArrayInputStream(storedSource.get()), job.getFileName())
                : readImportRows(job.getSourcePath());
            if (!jobService.setTotalRows(jobId, attempt, Math.max(0, rows.size() - 1))) {
                return;
            }
            if (!rows.isEmpty()) {
                Map<String, Integer> index = headerIndex(rows.get(0));
                for (int rowIndex = Math.max(1, count(job.getProcessedRows()) + 1);
                     rowIndex < rows.size(); rowIndex++) {
                    String[] row = rows.get(rowIndex);
                    if (isBlankRow(row)) {
                        rowService.skipRow(jobId, attempt, rowIndex, "Empty row");
                        continue;
                    }
                    Participant participant;
                    try {
                        participant = parseParticipant(row, index, race);
                    } catch (IllegalArgumentException invalidRow) {
                        rowService.skipRow(jobId, attempt, rowIndex, invalidRow.getMessage());
                        continue;
                    }
                    try {
                        rowService.importRow(jobId, attempt, rowIndex, participant);
                    } catch (ParticipantImportRowService.DuplicateValueException duplicate) {
                        rowService.skipRow(jobId, attempt, rowIndex, duplicate.getMessage());
                    } catch (ConflictException duplicateCredential) {
                        if (!"CONFLICT".equals(duplicateCredential.getCode())) {
                            throw duplicateCredential;
                        }
                        rowService.skipRow(jobId, attempt, rowIndex, "Duplicate credential");
                    }
                }
            }
            if (jobService.complete(jobId, attempt) && job.getSourcePath() != null && !job.getSourcePath().isBlank()) {
                try {
                    Files.deleteIfExists(Paths.get(job.getSourcePath()));
                } catch (IOException cleanupError) {
                    log.warn("Could not clean completed legacy import source for job {}", jobId, cleanupError);
                }
            }
        } catch (Exception ex) {
            if (jobService.fail(jobId, attempt, ex)) {
                log.warn("Import job {} failed on attempt {}: {}", jobId, attempt, ex.getMessage());
            }
        }
    }

    private int count(Integer value) {
        return value == null ? 0 : value;
    }

    private Participant parseParticipant(String[] row, Map<String, Integer> index, Race race) {
        validateImportedFieldLengths(row, index);
        Participant participant = new Participant();
        participant.setRace(race);
        participant.setFirstName(requiredCell(row, index, "firstName"));
        participant.setLastName(requiredCell(row, index, "lastName"));
        String email = requiredCell(row, index, "email").toLowerCase(Locale.ROOT);
        if (!EMAIL.matcher(email).matches()) {
            throw new IllegalArgumentException("Invalid email");
        }
        participant.setEmail(email);
        participant.setPhone(cell(row, index, "phone"));
        participant.setNationality(cell(row, index, "nationality"));
        participant.setBibNumber(cell(row, index, "bibNumber"));
        participant.setRfidTag(com.racetracker.service.RfidTagValues.canonical(cell(row, index, "rfidTag")));
        participant.setRegistrationSource(
            Optional.ofNullable(cell(row, index, "registrationSource")).orElse("CSV_IMPORT"));
        participant.setStatus(parseStatusNullable(cell(row, index, "status"),
            Participant.ParticipantStatus.REGISTERED));
        participant.setGender(parseGenderNullable(cell(row, index, "gender")));
        try {
            participant.setIsTrackingEnabled(parseBoolean(cell(row, index, "isTrackingEnabled"), true));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Invalid isTrackingEnabled (use true/false)");
        }
        String dob = cell(row, index, "dateOfBirth");
        if (dob != null && !dob.isBlank()) {
            LocalDate dateOfBirth;
            try {
                dateOfBirth = LocalDate.parse(dob);
            } catch (java.time.DateTimeException invalidDate) {
                throw new IllegalArgumentException("Invalid dateOfBirth (use YYYY-MM-DD)");
            }
            if (dateOfBirth.isAfter(LocalDate.now())) {
                throw new IllegalArgumentException("Invalid dateOfBirth (in the future)");
            }
            participant.setDateOfBirth(dateOfBirth);
        }
        String categoryId = cell(row, index, "categoryId");
        if (categoryId != null && !categoryId.isBlank()) {
            UUID parsedCategoryId;
            try {
                parsedCategoryId = UUID.fromString(categoryId);
            } catch (IllegalArgumentException invalidId) {
                throw new IllegalArgumentException("Invalid categoryId");
            }
            participant.setCategory(resolveCategory(race.getId(), parsedCategoryId));
        }
        participant.setPaymentStatus(com.racetracker.service.RegistrationFees.requiresPayment(participant, race)
            ? Participant.PaymentStatus.UNPAID : Participant.PaymentStatus.WAIVED);
        participant.setRegisteredAt(OffsetDateTime.now());
        return participant;
    }

    private List<String[]> readImportRows(String filePath) throws IOException {
        try (InputStream input = new FileInputStream(filePath)) {
            return readImportRows(input, filePath);
        }
    }

    private List<String[]> readImportRows(InputStream input, String fileName) throws IOException {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) {
            return readExcelRows(input);
        }
        try (CSVReader reader = new CSVReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            return reader.readAll();
        } catch (com.opencsv.exceptions.CsvException ex) {
            throw new IOException("Failed to parse CSV import file", ex);
        }
    }

    private List<String[]> readExcelRows(InputStream input) throws IOException {
        List<String[]> rows = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(input)) {
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
                map.putIfAbsent(canonicalHeader(headers[i]), i);
            }
        }
        return map;
    }

    static String canonicalHeader(String header) {
        String value = header.startsWith("\uFEFF") ? header.substring(1) : header;
        return value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private String cell(String[] row, Map<String, Integer> index, String key) {
        Integer i = index.get(canonicalHeader(key));
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

    private void validateImportedFieldLengths(String[] row, Map<String, Integer> index) {
        limited(cell(row, index, "firstName"), "firstName", 100);
        limited(cell(row, index, "lastName"), "lastName", 100);
        String email = cell(row, index, "email");
        limited(email == null ? null : email.toLowerCase(Locale.ROOT), "email", 255);
        limited(cell(row, index, "phone"), "phone", 20);
        limited(cell(row, index, "nationality"), "nationality", 100);
        limited(cell(row, index, "bibNumber"), "bibNumber", 20);
        limited(cell(row, index, "rfidTag"), "rfidTag", 100);
        limited(Optional.ofNullable(cell(row, index, "registrationSource")).orElse("CSV_IMPORT"),
            "registrationSource", 50);
    }

    private void limited(String value, String field, int maxLength) {
        if (value != null && value.codePointCount(0, value.length()) > maxLength) {
            throw new IllegalArgumentException("Field exceeds maximum length: " + field);
        }
    }

    private Participant.ParticipantStatus parseStatusNullable(String input, Participant.ParticipantStatus fallback) {
        if (input == null || input.isBlank()) {
            return fallback;
        }
        try {
            return Participant.ParticipantStatus.valueOf(input.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Invalid status");
        }
    }

    private Participant.Gender parseGenderNullable(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        try {
            return Participant.Gender.valueOf(input.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("Invalid gender");
        }
    }

    private boolean parseBoolean(String input, boolean fallback) {
        if (input == null || input.isBlank()) return fallback;
        if ("true".equalsIgnoreCase(input) || "1".equals(input) || "yes".equalsIgnoreCase(input)) {
            return true;
        }
        if ("false".equalsIgnoreCase(input) || "0".equals(input) || "no".equalsIgnoreCase(input)) {
            return false;
        }
        throw new IllegalArgumentException("Invalid boolean value");
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
