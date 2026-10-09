package com.racetracker.service;

import java.util.UUID;

public interface CertificateService {

    byte[] generate(UUID raceId, UUID participantId);
}
