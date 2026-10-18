package com.racetracker.config;

import com.racetracker.service.MediaStorage;
import com.racetracker.service.impl.AwsS3MediaStorage;
import com.racetracker.service.impl.LocalMediaStorage;
import com.racetracker.service.impl.MinioMediaStorage;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MediaStorageConfigurationTest {

    @TempDir
    Path tempDir;

    private final MediaStorageConfiguration configuration = new MediaStorageConfiguration();

    @Test
    void selectsLocalStorageByDefault() {
        RaceTrackerProperties properties = new RaceTrackerProperties();
        properties.getStorage().setUploadDir(tempDir.toString());

        MediaStorage storage = configuration.mediaStorage(properties);

        assertThat(storage).isInstanceOf(LocalMediaStorage.class);
    }

    @Test
    void selectsAwsS3WhenEnabled() {
        RaceTrackerProperties properties = new RaceTrackerProperties();
        properties.getStorage().setProvider("aws-s3");
        properties.getStorage().getAws().setEnabled(true);
        properties.getStorage().getAws().setBucket("race-media");

        MediaStorage storage = configuration.mediaStorage(properties);

        assertThat(storage).isInstanceOf(AwsS3MediaStorage.class);
    }

    @Test
    void selectsMinioWhenEnabled() {
        RaceTrackerProperties properties = new RaceTrackerProperties();
        properties.getStorage().setProvider("minio");
        properties.getStorage().getMinio().setEnabled(true);
        properties.getStorage().getMinio().setBucket("race-media");
        properties.getStorage().getMinio().setEndpoint("http://minio:9000");
        properties.getStorage().getMinio().setAccessKey("minio-access");
        properties.getStorage().getMinio().setSecretKey("minio-secret");

        MediaStorage storage = configuration.mediaStorage(properties);

        assertThat(storage).isInstanceOf(MinioMediaStorage.class);
    }

    @Test
    void bindsMinioEndpointRegionCredentialsAndPathStyleSettings() {
        RaceTrackerProperties properties = new Binder(new MapConfigurationPropertySource(Map.of(
            "racetracker.storage.provider", "minio",
            "racetracker.storage.minio.enabled", true,
            "racetracker.storage.minio.bucket", "race-media",
            "racetracker.storage.minio.endpoint", "https://minio.example.test:9000",
            "racetracker.storage.minio.region", "eu-west-1",
            "racetracker.storage.minio.access-key", "minio-access",
            "racetracker.storage.minio.secret-key", "minio-secret",
            "racetracker.storage.minio.path-style-access", true
        ))).bind("racetracker", Bindable.of(RaceTrackerProperties.class))
            .orElseThrow(() -> new AssertionError("Expected RaceTrackerProperties to bind"));

        assertThat(properties.getStorage().getProvider()).isEqualTo("minio");
        assertThat(properties.getStorage().getMinio().isEnabled()).isTrue();
        assertThat(properties.getStorage().getMinio().getBucket()).isEqualTo("race-media");
        assertThat(properties.getStorage().getMinio().getEndpoint()).isEqualTo("https://minio.example.test:9000");
        assertThat(properties.getStorage().getMinio().getRegion()).isEqualTo("eu-west-1");
        assertThat(properties.getStorage().getMinio().getAccessKey()).isEqualTo("minio-access");
        assertThat(properties.getStorage().getMinio().getSecretKey()).isEqualTo("minio-secret");
        assertThat(properties.getStorage().getMinio().isPathStyleAccess()).isTrue();
    }

    @Test
    void rejectsSelectedDisabledProvider() {
        RaceTrackerProperties properties = new RaceTrackerProperties();
        properties.getStorage().setProvider("aws-s3");

        assertThatThrownBy(() -> configuration.mediaStorage(properties))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("disabled: aws-s3");
    }

    @Test
    void rejectsSelectedDisabledMinioProvider() {
        RaceTrackerProperties properties = new RaceTrackerProperties();
        properties.getStorage().setProvider("minio");

        assertThatThrownBy(() -> configuration.mediaStorage(properties))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("disabled: minio");
    }

    @Test
    void rejectsUnsupportedProvider() {
        RaceTrackerProperties properties = new RaceTrackerProperties();
        properties.getStorage().setProvider("azure-blob");

        assertThatThrownBy(() -> configuration.mediaStorage(properties))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Unsupported media storage provider");
    }
}
