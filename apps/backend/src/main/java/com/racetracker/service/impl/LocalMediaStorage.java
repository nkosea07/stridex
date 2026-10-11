package com.racetracker.service.impl;

import com.racetracker.config.RaceTrackerProperties;
import com.racetracker.exception.ApiException;
import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.service.MediaStorage;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Local-disk media storage. Keys are relative paths under the configured upload directory;
 * all keys are normalized and confined to that directory.
 */
@Component
public class LocalMediaStorage implements MediaStorage {

    private static final long MAX_BYTES = 15L * 1024 * 1024;
    private static final List<String> ALLOWED_CONTENT_TYPES = List.of(
        "image/jpeg", "image/png", "image/webp", "image/gif", "video/mp4", "video/webm"
    );

    private final Path root;

    public LocalMediaStorage(RaceTrackerProperties properties) {
        this.root = Paths.get(properties.getStorage().getUploadDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to create upload directory", ex);
        }
    }

    @Override
    public String store(MultipartFile file, String subdir) {
        if (file == null || file.isEmpty()) {
            throw new ApiException("A file is required");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ApiException("File exceeds the maximum allowed size of 15MB");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new ApiException("Unsupported media type: " + contentType);
        }
        String key = keyFor(subdir, extensionFor(contentType));
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (IOException ex) {
            throw new ApiException("Failed to store file");
        }
        return key;
    }

    @Override
    public String storeBytes(byte[] content, String extension, String subdir) {
        if (content == null || content.length == 0) {
            throw new ApiException("Stored content must not be empty");
        }
        String key = keyFor(subdir, extension);
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException ex) {
            throw new ApiException("Failed to store file");
        }
        return key;
    }

    @Override
    public byte[] load(String key) {
        Path target = resolve(key);
        if (!Files.exists(target)) {
            throw new ResourceNotFoundException("Media file not found");
        }
        try {
            return Files.readAllBytes(target);
        } catch (IOException ex) {
            throw new ApiException("Failed to read file");
        }
    }

    @Override
    public void delete(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        Path target = resolve(key);
        try {
            Files.deleteIfExists(target);
        } catch (IOException ignored) {
            // best-effort cleanup
        }
    }

    private Path resolve(String key) {
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw new ApiException("Invalid storage path");
        }
        return target;
    }

    private String keyFor(String subdir, String extension) {
        return (subdir == null || subdir.isBlank() ? "" : subdir.replaceAll("[^a-zA-Z0-9-]", "") + "/")
            + UUID.randomUUID() + extension;
    }

    private String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            case "video/mp4" -> ".mp4";
            case "video/webm" -> ".webm";
            default -> "";
        };
    }
}
