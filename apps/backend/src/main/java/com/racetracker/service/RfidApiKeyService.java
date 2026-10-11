package com.racetracker.service;

public interface RfidApiKeyService {
    String generateKey();
    String hashKey(String apiKey);
}
