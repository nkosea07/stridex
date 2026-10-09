package com.racetracker.service.impl;

import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.racetracker.dto.result.RaceResultDto;
import com.racetracker.exception.ApiException;
import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.model.Race;
import com.racetracker.repository.RaceRepository;
import com.racetracker.service.ResultExportService;
import com.racetracker.service.ResultService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class ResultExportServiceImpl implements ResultExportService {

    private static final String[] HEADERS = {
        "Bib", "Name", "Category", "Status", "Gun Time", "Chip Time",
        "Overall", "Gender", "Category Pos", "Penalty (s)"
    };

    private final ResultService resultService;
    private final RaceRepository raceRepository;

    public ResultExportServiceImpl(ResultService resultService, RaceRepository raceRepository) {
        this.resultService = resultService;
        this.raceRepository = raceRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public ExportedFile export(UUID raceId, String format) {
        Race race = raceRepository.findById(raceId)
            .orElseThrow(() -> new ResourceNotFoundException("Race not found"));
        List<RaceResultDto> results = resultService.listByRace(raceId);
        String safeName = (race.getName() == null ? "race" : race.getName())
            .replaceAll("[^a-zA-Z0-9-_]+", "-")
            .toLowerCase(Locale.ROOT);
        String normalized = format == null ? "csv" : format.trim().toLowerCase(Locale.ROOT);

        return switch (normalized) {
            case "csv" -> new ExportedFile(toCsv(results), "text/csv", safeName + "-results.csv");
            case "pdf" -> new ExportedFile(toPdf(race, results), "application/pdf", safeName + "-results.pdf");
            case "xlsx" -> new ExportedFile(toXlsx(results), "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", safeName + "-results.xlsx");
            default -> throw new ApiException("Unsupported export format: " + format + " (use csv, pdf or xlsx)");
        };
    }

    private byte[] toCsv(List<RaceResultDto> results) {
        StringBuilder builder = new StringBuilder();
        builder.append(String.join(",", HEADERS)).append('\n');
        for (RaceResultDto result : results) {
            builder.append(csv(cell(result, 0))).append(',')
                .append(csv(cell(result, 1))).append(',')
                .append(csv(cell(result, 2))).append(',')
                .append(csv(cell(result, 3))).append(',')
                .append(csv(cell(result, 4))).append(',')
                .append(csv(cell(result, 5))).append(',')
                .append(csv(cell(result, 6))).append(',')
                .append(csv(cell(result, 7))).append(',')
                .append(csv(cell(result, 8))).append(',')
                .append(csv(cell(result, 9))).append('\n');
        }
        return builder.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private String cell(RaceResultDto result, int column) {
        return switch (column) {
            case 0 -> result.getBibNumber();
            case 1 -> result.getParticipant() != null
                ? ((result.getParticipant().getFirstName() == null ? "" : result.getParticipant().getFirstName())
                    + " " + (result.getParticipant().getLastName() == null ? "" : result.getParticipant().getLastName())).trim()
                : null;
            case 2 -> result.getCategory();
            case 3 -> result.getStatus();
            case 4 -> result.getGunTime();
            case 5 -> result.getChipTime();
            case 6 -> result.getOverallPosition() != null ? String.valueOf(result.getOverallPosition()) : null;
            case 7 -> result.getGenderPosition() != null ? String.valueOf(result.getGenderPosition()) : null;
            case 8 -> result.getCategoryPosition() != null ? String.valueOf(result.getCategoryPosition()) : null;
            case 9 -> result.getPenaltySeconds() != null ? String.valueOf(result.getPenaltySeconds()) : null;
            default -> null;
        };
    }

    private String csv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private byte[] toPdf(Race race, List<RaceResultDto> results) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf, PageSize.A4.rotate());
            document.add(new Paragraph(race.getName() + " - Official Results").setBold().setFontSize(16)
                .setTextAlignment(TextAlignment.CENTER).setMarginBottom(12));

            Table table = new Table(HEADERS.length);
            for (String header : HEADERS) {
                table.addHeaderCell(new Cell().add(new Paragraph(header).setBold().setFontSize(9)));
            }
            for (RaceResultDto result : results) {
                for (int i = 0; i < HEADERS.length; i++) {
                    table.addCell(new Cell().add(new Paragraph(nonNull(cell(result, i))).setFontSize(9)));
                }
            }
            document.add(table);
            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new ApiException("Failed to export results as PDF: " + e.getMessage());
        }
    }

    private byte[] toXlsx(List<RaceResultDto> results) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Results");
            Row header = sheet.createRow(0);
            for (int i = 0; i < HEADERS.length; i++) {
                header.createCell(i).setCellValue(HEADERS[i]);
            }
            int rowIndex = 1;
            for (RaceResultDto result : results) {
                Row row = sheet.createRow(rowIndex++);
                for (int i = 0; i < HEADERS.length; i++) {
                    row.createCell(i).setCellValue(nonNull(cell(result, i)));
                }
            }
            for (int i = 0; i < HEADERS.length; i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(baos);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new ApiException("Failed to export results as Excel: " + e.getMessage());
        }
    }

    private String nonNull(String value) {
        return value == null ? "" : value;
    }
}
