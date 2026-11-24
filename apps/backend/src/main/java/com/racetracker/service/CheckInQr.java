package com.racetracker.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.racetracker.model.Participant;
import com.racetracker.model.Race;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

public final class CheckInQr {

    public static final String PREFIX = "AMS-PARTICIPANT-";

    private static final Set<Participant.ParticipantStatus> CHECK_IN_READY = EnumSet.of(
        Participant.ParticipantStatus.REGISTERED,
        Participant.ParticipantStatus.CONFIRMED
    );

    private static final Set<Race.RaceStatus> RACE_OVER = EnumSet.of(Race.RaceStatus.COMPLETED, Race.RaceStatus.CANCELLED);

    private CheckInQr() {
    }

    public static String content(UUID participantId) {
        return PREFIX + participantId;
    }

    public static boolean available(Participant participant) {
        Race race = participant.getRace();
        return participant.getId() != null
            && CHECK_IN_READY.contains(participant.getStatus())
            && race != null
            && !RACE_OVER.contains(race.getStatus());
    }

    public static byte[] png(UUID participantId, int size) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(content(participantId), BarcodeFormat.QR_CODE, size, size);
            try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                MatrixToImageWriter.writeToStream(matrix, "PNG", output);
                return output.toByteArray();
            }
        } catch (WriterException | IOException ex) {
            throw new IllegalStateException("Could not generate the check-in QR code", ex);
        }
    }
}
