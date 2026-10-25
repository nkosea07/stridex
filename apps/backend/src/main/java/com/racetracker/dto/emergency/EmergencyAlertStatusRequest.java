package com.racetracker.dto.emergency;

import jakarta.validation.constraints.NotBlank;

public record EmergencyAlertStatusRequest(@NotBlank String status) {
}
