package com.racetracker.dto.auth;

import java.util.List;

public record MfaRecoveryCodesResponse(List<String> recoveryCodes) {
}
