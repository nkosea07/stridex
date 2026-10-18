package com.racetracker.service.impl;

import com.racetracker.exception.ApiException;
import com.racetracker.exception.ResourceNotFoundException;
import com.racetracker.service.MediaStorage;
import jakarta.annotation.PreDestroy;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.ObjectCannedACL;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

abstract class S3MediaStorageSupport implements MediaStorage {

    private static final long MAX_BYTES = 15L * 1024 * 1024;
    private static final List<String> ALLOWED_CONTENT_TYPES = List.of(
        "image/jpeg", "image/png", "image/webp", "image/gif", "video/mp4", "video/webm"
    );

    private final S3Client client;
    private final String bucket;

    protected S3MediaStorageSupport(S3Client client, String bucket) {
        if (client == null) {
            throw new IllegalArgumentException("S3 client is required");
        }
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("Object storage bucket is required");
        }
        this.client = client;
        this.bucket = bucket;
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
        try {
            put(key, file.getBytes(), contentType);
        } catch (IOException ex) {
            throw new ApiException("Failed to read file");
        }
        return key;
    }

    @Override
    public String storeBytes(byte[] content, String extension, String subdir) {
        if (content == null || content.length == 0) {
            throw new ApiException("Stored content must not be empty");
        }
        if (content.length > MAX_BYTES) {
            throw new ApiException("Stored content exceeds the maximum allowed size of 15MB");
        }
        String key = keyFor(subdir, extension);
        put(key, content, contentTypeFor(extension));
        return key;
    }

    @Override
    public byte[] load(String key) {
        try {
            ResponseBytes<GetObjectResponse> response = client.getObjectAsBytes(GetObjectRequest.builder()
                .bucket(bucket)
                .key(requireKey(key))
                .build());
            return response.asByteArray();
        } catch (NoSuchKeyException ex) {
            throw new ResourceNotFoundException("Media file not found");
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                throw new ResourceNotFoundException("Media file not found");
            }
            throw new ApiException("Failed to read file");
        } catch (SdkException ex) {
            throw new ApiException("Failed to read file");
        }
    }

    @Override
    public void delete(String key) {
        if (key == null || key.isBlank()) {
            return;
        }
        try {
            client.deleteObject(DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(requireKey(key))
                .build());
        } catch (SdkException ignored) {
            throw new ApiException("Failed to delete file");
        }
    }

    @PreDestroy
    void closeClient() {
        client.close();
    }

    private void put(String key, byte[] content, String contentType) {
        try {
            client.putObject(
                PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType)
                    .acl(ObjectCannedACL.PRIVATE)
                    .build(),
                RequestBody.fromBytes(content)
            );
        } catch (SdkException ex) {
            throw new ApiException("Failed to store file");
        }
    }

    private String requireKey(String key) {
        if (key == null || key.isBlank()
            || key.startsWith("/")
            || key.contains("\\")
            || key.contains("..")) {
            throw new ApiException("Invalid storage key");
        }
        return key;
    }

    private String keyFor(String subdir, String extension) {
        String safeSubdir = subdir == null ? "" : subdir.replaceAll("[^a-zA-Z0-9-]", "");
        String safeExtension = extension == null ? "" : extension.replaceAll("[^a-zA-Z0-9.]", "");
        return (safeSubdir.isBlank() ? "" : safeSubdir + "/") + UUID.randomUUID() + safeExtension;
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

    private String contentTypeFor(String extension) {
        if (extension == null) {
            return "application/octet-stream";
        }
        return switch (extension.toLowerCase(Locale.ROOT)) {
            case ".jpg", ".jpeg" -> "image/jpeg";
            case ".png" -> "image/png";
            case ".webp" -> "image/webp";
            case ".gif" -> "image/gif";
            case ".mp4" -> "video/mp4";
            case ".webm" -> "video/webm";
            default -> "application/octet-stream";
        };
    }
}
