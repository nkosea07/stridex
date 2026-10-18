package com.racetracker.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * Storage backend for race media. Implementations may use local disk or private object storage;
 * the active backend is selected by configuration so callers stay storage-agnostic.
 *
 * <p>The current media API intentionally keeps byte-loading semantics. Object-storage
 * implementations therefore do not expose public objects or signed URLs; signed URL delivery
 * remains a follow-up API change rather than a storage adapter concern.</p>
 */
public interface MediaStorage {

    String store(MultipartFile file, String subdir);

    String storeBytes(byte[] content, String extension, String subdir);

    byte[] load(String key);

    void delete(String key);
}
