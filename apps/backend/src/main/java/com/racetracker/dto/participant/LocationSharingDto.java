package com.racetracker.dto.participant;

import java.util.UUID;

public record LocationSharingDto(UUID participantId, boolean enabled) {}
