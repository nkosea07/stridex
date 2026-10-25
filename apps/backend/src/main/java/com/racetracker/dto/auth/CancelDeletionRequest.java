package com.racetracker.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CancelDeletionRequest(@NotBlank @Email String email, @NotBlank String password) {
}
