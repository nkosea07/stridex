package com.racetracker.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "racetracker")
@Getter
@Setter
public class RaceTrackerProperties {

    private Api api = new Api();
    private Bib bib = new Bib();
    private Tracking tracking = new Tracking();
    private Email email = new Email();
    private Storage storage = new Storage();
    private Rfid rfid = new Rfid();
    private Push push = new Push();
    private Payments payments = new Payments();
    private Notifications notifications = new Notifications();
    private OAuth oauth = new OAuth();
    private Privacy privacy = new Privacy();

    @Getter
    @Setter
    public static class Api {
        private String version;
    }

    @Getter
    @Setter
    public static class Storage {
        private String uploadDir = "uploads";
        private String provider = "local";
        private Aws aws = new Aws();
        private Minio minio = new Minio();
    }

    @Getter
    @Setter
    public static class Aws {
        private boolean enabled = false;
        private String bucket;
        private String region = "us-east-1";
        private String endpoint;
        private String accessKey;
        private String secretKey;
        private boolean pathStyleAccess = false;
    }

    @Getter
    @Setter
    public static class Minio {
        private boolean enabled = false;
        private String bucket;
        private String region = "us-east-1";
        private String endpoint;
        private String accessKey;
        private String secretKey;
        private boolean pathStyleAccess = true;
    }

    @Getter
    @Setter
    public static class Bib {
        private int qrCodeSize;
        private String defaultPrefix;
    }

    @Getter
    @Setter
    public static class Tracking {
        private int gpsUpdateIntervalSeconds;
        private int checkpointRadiusMeters;
        private double accuracyThresholdMeters = 100;
        private double maxPlausibleSpeedKmh = 60;
        private double smoothingFactor = 0.4;
        private double minDistanceForAccumulationMeters = 5;
        private int maxTrackPoints = 1000;
        private int heatmapScale = 3;
        private int retentionDays = 0;
    }

    @Getter
    @Setter
    public static class Email {
        private boolean enabled = false;
        private String fromAddress;
        private String fromName;
        private String frontendBaseUrl = "http://localhost:5173";
        private String verificationPath = "/verify-email";
        private String resetPasswordPath = "/reset-password";
    }

    @Getter
    @Setter
    public static class Rfid {
        private boolean simulationEnabled = true;
        private String streamKey = "race.rfid.ingest";
        private String consumerGroup = "race-tracker-rfid";
        private String consumerName = "backend-worker";
        private int workerBatchSize = 50;
        private long workerPollTimeoutMillis = 1000;
        private long workerFixedDelayMillis = 500;
        private long recoveryFixedDelayMillis = 5000;
        private int readerSilenceTimeoutSeconds = 120;
        private int gatewayBacklogDegradedThreshold = 100;
    }

    @Getter
    @Setter
    public static class Push {
        private boolean enabled = false;
        private String serviceAccountJson;
        private String projectId;
    }

    @Getter
    @Setter
    public static class Payments {
        private boolean enabled = false;
        private String baseUrl = "https://api.test.sandbox.pesepay.com/payments-engine/v1";
        private String integrationKey;
        private String encryptionKey;
        private String currency = "USD";
        private String resultUrl;
        private String returnUrl;
        private String refundPath = "/payments/refund";
    }

    @Getter
    @Setter
    public static class Notifications {
        private boolean smsEnabled = false;
        private boolean whatsappEnabled = false;
    }

    @Getter
    @Setter
    public static class OAuth {
        private boolean enabled = true;
        private String googleClientId;
    }

    @Getter
    @Setter
    public static class Privacy {
        private int deletionGraceDays = 30;
    }
}
