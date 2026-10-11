package com.racetracker.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * Storage backend for race media. Implementations may use local disk or object storage; the
 * active backend is selected by configuration so callers stay storage-agnostic.
 */
public interface MediaStorage {

    String store(MultipartFile file, String subdir);

    String storeBytes(byte[] content, String extension, String subdir);

    byte[] load(String key);

    void delete(String key);
}
