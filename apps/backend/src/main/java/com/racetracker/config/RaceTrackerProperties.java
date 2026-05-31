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

    @Getter
    @Setter
    public static class Api {
        private String version;
    }

    @Getter
    @Setter
    public static class Storage {
        private String uploadDir = "uploads";
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
}
