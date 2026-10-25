package com.racetracker.dto.participant;

import jakarta.validation.constraints.NotBlank;

public record ExternalRefundAttemptRequest(@NotBlank String reason) {
}
