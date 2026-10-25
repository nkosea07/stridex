package com.racetracker.service.impl;

import com.racetracker.config.RaceTrackerProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Locale;

@Component
public class MediaStorageScope {

    private final String id;

    public MediaStorageScope(RaceTrackerProperties properties) {
        RaceTrackerProperties.Storage storage = properties.getStorage();
        String provider = storage.getProvider().trim().toLowerCase(Locale.ROOT);
        this.id = switch (provider) {
            case "aws-s3" -> String.join("|", provider, storage.getAws().getRegion(),
                storage.getAws().getBucket(), normalized(storage.getAws().getEndpoint()));
            case "minio" -> String.join("|", provider, storage.getMinio().getRegion(),
                storage.getMinio().getBucket(), normalized(storage.getMinio().getEndpoint()));
            case "local" -> provider + "|" + Path.of(storage.getUploadDir()).toAbsolutePath().normalize();
            default -> throw new IllegalStateException("Unknown media storage provider");
        };
    }

    public String id() {
        return id;
    }

    private static String normalized(String value) {
        return value == null ? "" : value.trim().replaceAll("/+$", "");
    }
}
