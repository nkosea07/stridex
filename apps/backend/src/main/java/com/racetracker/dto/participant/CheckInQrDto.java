package com.racetracker.dto.participant;

import java.util.UUID;

public record CheckInQrDto(UUID participantId, String content, String imagePngBase64) {}
