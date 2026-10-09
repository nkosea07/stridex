package com.racetracker.rfidreaderdemo.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "adapter")
public class DemoReaderProperties {

    private final Gateway gateway = new Gateway();
    private final Reader reader = new Reader();
    private final Mock mock = new Mock();

    public Gateway getGateway() {
        return gateway;
    }

    public Reader getReader() {
        return reader;
    }

    public Mock getMock() {
        return mock;
    }

    public static class Gateway {
        @NotBlank
        private String baseUrl = "http://localhost:8090";

        @NotBlank
        private String readsPath = "/reads";

        @Min(1)
        private int batchSize = 25;

        @Min(100)
        private int flushIntervalMs = 500;

        @Min(1000)
        private int timeoutMs = 5000;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getReadsPath() {
            return readsPath;
        }

        public void setReadsPath(String readsPath) {
            this.readsPath = readsPath;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public int getFlushIntervalMs() {
            return flushIntervalMs;
        }

        public void setFlushIntervalMs(int flushIntervalMs) {
            this.flushIntervalMs = flushIntervalMs;
        }

        public int getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(int timeoutMs) {
            this.timeoutMs = timeoutMs;
        }
    }

    public static class Reader {
        @NotBlank
        private String mode = "bridge";

        @NotBlank
        private String name = "compact-uhf-demo";

        @NotBlank
        private String hardwareModel = "compact-fixed-uhf";

        @Min(1)
        private int expectedAntennas = 2;

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getHardwareModel() {
            return hardwareModel;
        }

        public void setHardwareModel(String hardwareModel) {
            this.hardwareModel = hardwareModel;
        }

        public int getExpectedAntennas() {
            return expectedAntennas;
        }

        public void setExpectedAntennas(int expectedAntennas) {
            this.expectedAntennas = expectedAntennas;
        }
    }

    public static class Mock {
        private boolean enabled = true;

        @Min(200)
        private int emitIntervalMs = 2500;

        @NotBlank
        private String tagPrefix = "DEMO-TAG";

        @NotBlank
        private String antenna = "A1";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getEmitIntervalMs() {
            return emitIntervalMs;
        }

        public void setEmitIntervalMs(int emitIntervalMs) {
            this.emitIntervalMs = emitIntervalMs;
        }

        public String getTagPrefix() {
            return tagPrefix;
        }

        public void setTagPrefix(String tagPrefix) {
            this.tagPrefix = tagPrefix;
        }

        public String getAntenna() {
            return antenna;
        }

        public void setAntenna(String antenna) {
            this.antenna = antenna;
        }
    }
}
