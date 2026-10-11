package com.racetracker.service.impl;

import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.TextAlignment;
import com.racetracker.dto.result.RaceResultDto;
import com.racetracker.exception.ApiException;
import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.model.Race;
import com.racetracker.repository.RaceRepository;
import com.racetracker.service.CertificateService;
import com.racetracker.service.ResultService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class CertificateServiceImpl implements CertificateService {

    private final ResultService resultService;
    private final RaceRepository raceRepository;

    public CertificateServiceImpl(ResultService resultService, RaceRepository raceRepository) {
        this.resultService = resultService;
        this.raceRepository = raceRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] generate(UUID raceId, UUID participantId) {
        RaceResultDto result = resultService.getByRaceAndParticipant(raceId, participantId);
        if (!result.isOfficial()) {
            throw new ApiException("Certificates are only available for official finishers");
        }
        Race race = raceRepository.findById(raceId)
            .orElseThrow(() -> new ResourceNotFoundException("Race not found"));

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf, PageSize.A4.rotate());
            document.setMargins(40, 40, 40, 40);

            document.add(new Paragraph("Certificate of Completion")
                .setBold().setFontSize(28).setTextAlignment(TextAlignment.CENTER).setMarginBottom(18));
            document.add(new Paragraph(race.getName())
                .setFontSize(18).setTextAlignment(TextAlignment.CENTER).setMarginBottom(30));
            document.add(new Paragraph("This certifies that")
                .setFontSize(12).setTextAlignment(TextAlignment.CENTER));
            document.add(new Paragraph(participantName(result))
                .setBold().setFontSize(26).setTextAlignment(TextAlignment.CENTER).setMarginTop(10).setMarginBottom(16));
            document.add(new Paragraph("completed the " + nonNull(result.getCategory(), "race") + " category")
                .setFontSize(12).setTextAlignment(TextAlignment.CENTER));
            document.add(new Paragraph("Official time: " + nonNull(result.getTotalTime(), "-"))
                .setBold().setFontSize(16).setTextAlignment(TextAlignment.CENTER).setMarginTop(8));
            document.add(new Paragraph("Overall position: " + position(result.getOverallPosition()))
                .setFontSize(12).setTextAlignment(TextAlignment.CENTER));
            document.add(new Paragraph("Date: " + (race.getRaceDate() != null
                ? race.getRaceDate().format(DateTimeFormatter.ISO_LOCAL_DATE) : "-"))
                .setFontSize(12).setTextAlignment(TextAlignment.CENTER).setMarginTop(20));

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new ApiException("Failed to generate certificate: " + e.getMessage());
        }
    }

    private String participantName(RaceResultDto result) {
        if (result.getParticipant() != null) {
            String name = ((result.getParticipant().getFirstName() == null ? "" : result.getParticipant().getFirstName())
                + " " + (result.getParticipant().getLastName() == null ? "" : result.getParticipant().getLastName())).trim();
            if (!name.isBlank()) {
                return name;
            }
        }
        return nonNull(result.getBibNumber(), "Athlete");
    }

    private String position(Integer value) {
        return value == null ? "-" : String.valueOf(value);
    }

    private String nonNull(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
