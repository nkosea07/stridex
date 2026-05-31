package com.racetracker.service.impl;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.VerticalAlignment;
import com.racetracker.exception.ApiException;
import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.model.Participant;
import com.racetracker.model.Race;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.RaceRepository;
import com.racetracker.service.BibService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BibServiceImpl implements BibService {

    private final ParticipantRepository participantRepository;
    private final RaceRepository raceRepository;

    @Override
    @Transactional(readOnly = true)
    public byte[] generateBibPdf(UUID raceId, UUID participantId) {
        Participant participant = participantRepository.findByRaceIdAndId(raceId, participantId)
            .orElseThrow(() -> new ResourceNotFoundException("Participant not found"));

        return createPdf(List.of(participant));
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] generateBatchBibsPdf(UUID raceId, List<UUID> participantIds) {
        List<Participant> participants = participantRepository.findAllById(participantIds);
        if (participants.isEmpty()) {
            throw new ApiException("No participants found for batch printing");
        }
        return createPdf(participants);
    }

    private byte[] createPdf(List<Participant> participants) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PdfWriter writer = new PdfWriter(baos);
            PdfDocument pdf = new PdfDocument(writer);
            Document document = new Document(pdf, PageSize.A5.rotate());
            document.setMargins(20, 20, 20, 20);

            for (int i = 0; i < participants.size(); i++) {
                Participant p = participants.get(i);
                addBibPage(document, p);
                if (i < participants.size() - 1) {
                    document.add(new com.itextpdf.layout.element.AreaBreak());
                }
            }

            document.close();
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Failed to generate Bib PDF", e);
            throw new ApiException("Failed to generate Bib PDF: " + e.getMessage());
        }
    }

    private void addBibPage(Document document, Participant participant) throws Exception {
        Race race = participant.getRace();
        
        // Race Name
        document.add(new Paragraph(race.getName().toUpperCase())
            .setTextAlignment(TextAlignment.CENTER)
            .setFontSize(18)
            .setBold()
            .setMarginBottom(10));

        // Category
        String category = participant.getCategory() != null ? participant.getCategory().getName() : "General";
        document.add(new Paragraph(category)
            .setTextAlignment(TextAlignment.CENTER)
            .setFontSize(12)
            .setFontColor(ColorConstants.GRAY)
            .setMarginBottom(20));

        // Bib Number
        String bibNumber = participant.getBibNumber() != null ? participant.getBibNumber() : "---";
        document.add(new Paragraph(bibNumber)
            .setTextAlignment(TextAlignment.CENTER)
            .setFontSize(120)
            .setBold()
            .setMarginBottom(10));

        // Athlete Name
        document.add(new Paragraph(participant.getFullName().toUpperCase())
            .setTextAlignment(TextAlignment.CENTER)
            .setFontSize(24)
            .setBold()
            .setMarginBottom(20));

        // QR Code (Athlete ID or Profile Link)
        byte[] qrCode = generateQrCode("AMS-PARTICIPANT-" + participant.getId(), 100);
        ImageData imageData = ImageDataFactory.create(qrCode);
        Image image = new Image(imageData);
        image.setHorizontalAlignment(HorizontalAlignment.CENTER);
        document.add(image);
        
        // Emergency Contact
        if (participant.getEmergencyContactName() != null && participant.getEmergencyContactPhone() != null) {
            document.add(new Paragraph("ICE: " + participant.getEmergencyContactName() + " (" + participant.getEmergencyContactPhone() + ")")
                .setTextAlignment(TextAlignment.CENTER)
                .setFontSize(10)
                .setFontColor(ColorConstants.RED)
                .setFixedPosition(20, 20, 500));
        }
    }

    private byte[] generateQrCode(String text, int size) throws Exception {
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = qrCodeWriter.encode(text, BarcodeFormat.QR_CODE, size, size);
        
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", baos);
            return baos.toByteArray();
        }
    }
}
