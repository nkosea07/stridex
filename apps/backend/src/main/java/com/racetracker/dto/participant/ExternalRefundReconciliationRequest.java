package com.racetracker.dto.participant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ExternalRefundReconciliationRequest(
    @NotBlank String providerReference,
    @NotBlank String reason,
    @NotNull Outcome outcome
) {
    public enum Outcome { REFUNDED, NOT_REFUNDED }
}
