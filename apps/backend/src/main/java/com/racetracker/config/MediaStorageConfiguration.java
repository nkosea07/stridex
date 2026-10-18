package com.racetracker.config;

import com.racetracker.service.MediaStorage;
import com.racetracker.service.impl.AwsS3MediaStorage;
import com.racetracker.service.impl.LocalMediaStorage;
import com.racetracker.service.impl.MinioMediaStorage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;
import java.util.Locale;

@Configuration(proxyBeanMethods = false)
public class MediaStorageConfiguration {

    @Bean
    MediaStorage mediaStorage(RaceTrackerProperties properties) {
        RaceTrackerProperties.Storage storage = properties.getStorage();
        String provider = normalize(storage.getProvider());

        return switch (provider) {
            case "local" -> new LocalMediaStorage(properties);
            case "aws-s3" -> {
                requireEnabled(provider, storage.getAws() != null && storage.getAws().isEnabled());
                yield new AwsS3MediaStorage(buildAwsClient(storage.getAws()), storage.getAws().getBucket());
            }
            case "minio" -> {
                requireEnabled(provider, storage.getMinio() != null && storage.getMinio().isEnabled());
                yield new MinioMediaStorage(buildMinioClient(storage.getMinio()), storage.getMinio().getBucket());
            }
            default -> throw new IllegalStateException(
                "Unsupported media storage provider: " + (storage.getProvider() == null ? "" : storage.getProvider())
            );
        };
    }

    private S3Client buildAwsClient(RaceTrackerProperties.Aws settings) {
        if (settings == null) {
            throw new IllegalStateException("AWS S3 storage configuration is required");
        }
        S3ClientBuilder builder = baseClientBuilder(settings.getRegion(), settings.isPathStyleAccess(),
            settings.getAccessKey(), settings.getSecretKey());
        if (hasText(settings.getEndpoint())) {
            builder.endpointOverride(URI.create(settings.getEndpoint()));
        }
        return builder.build();
    }

    private S3Client buildMinioClient(RaceTrackerProperties.Minio settings) {
        if (settings == null) {
            throw new IllegalStateException("MinIO storage configuration is required");
        }
        if (!hasText(settings.getEndpoint())) {
            throw new IllegalStateException("MinIO storage requires an endpoint");
        }
        S3ClientBuilder builder = baseClientBuilder(settings.getRegion(), settings.isPathStyleAccess(),
            settings.getAccessKey(), settings.getSecretKey());
        builder.endpointOverride(URI.create(settings.getEndpoint()));
        return builder.build();
    }

    private S3ClientBuilder baseClientBuilder(
        String region,
        boolean pathStyleAccess,
        String accessKey,
        String secretKey
    ) {
        return S3Client.builder()
            .region(Region.of(hasText(region) ? region : "us-east-1"))
            .credentialsProvider(credentialsProvider(accessKey, secretKey))
            .serviceConfiguration(S3Configuration.builder()
                .pathStyleAccessEnabled(pathStyleAccess)
                .build());
    }

    private AwsCredentialsProvider credentialsProvider(String accessKey, String secretKey) {
        boolean hasAccessKey = hasText(accessKey);
        boolean hasSecretKey = hasText(secretKey);
        if (hasAccessKey != hasSecretKey) {
            throw new IllegalStateException("Object storage access and secret keys must be provided together");
        }
        if (!hasAccessKey) {
            return DefaultCredentialsProvider.create();
        }
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
    }

    private void requireEnabled(String provider, boolean enabled) {
        if (!enabled) {
            throw new IllegalStateException("Selected media storage provider is disabled: " + provider);
        }
    }

    private String normalize(String provider) {
        return provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
