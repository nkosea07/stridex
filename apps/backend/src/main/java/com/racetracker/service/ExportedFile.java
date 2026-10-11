package com.racetracker.service;

/**
 * A generated downloadable file returned by export services.
 */
public record ExportedFile(byte[] content, String contentType, String filename) {
}
