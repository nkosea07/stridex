package com.racetracker.dto.participant;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record ExternalRefundAttemptRequest(@NotBlank String reason, UUID paymentId) {
}
