package com.racetracker.service.impl;

import com.racetracker.config.RaceTrackerProperties;
import com.racetracker.exception.ApiException;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Component
public class FileStorageService {

    private static final long MAX_BYTES = 15L * 1024 * 1024;
    private static final List<String> ALLOWED_CONTENT_TYPES = List.of(
        "image/jpeg", "image/png", "image/webp", "image/gif", "video/mp4", "video/webm"
    );

    private final Path root;
    private final RaceTrackerProperties properties;

    public FileStorageService(RaceTrackerProperties properties) {
        this.properties = properties;
        this.root = Paths.get(properties.getStorage().getUploadDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to create upload directory", ex);
        }
    }

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
        String extension = extensionFor(contentType);
        String key = (subdir == null || subdir.isBlank() ? "" : subdir.replaceAll("[^a-zA-Z0-9-]", "") + "/")
            + UUID.randomUUID() + extension;
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw new ApiException("Invalid storage path");
        }
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (IOException ex) {
            throw new ApiException("Failed to store file: " + ex.getMessage());
        }
        return key;
    }

    public byte[] load(String key) {
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root) || !Files.exists(target)) {
            throw new com.racetracker.exception.ResourceNotFoundException("Media file not found");
        }
        try {
            return Files.readAllBytes(target);
        } catch (IOException ex) {
            throw new ApiException("Failed to read file: " + ex.getMessage());
        }
    }

    public void delete(String key) {
        try {
            Files.deleteIfExists(root.resolve(key).normalize());
        } catch (IOException ignored) {
            // best-effort cleanup
        }
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
