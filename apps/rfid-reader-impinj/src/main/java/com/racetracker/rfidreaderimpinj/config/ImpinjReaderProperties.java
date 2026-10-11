package com.racetracker.rfidreaderimpinj.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

@Validated
@ConfigurationProperties(prefix = "adapter")
public class ImpinjReaderProperties {

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
        private int batchSize = 50;

        @Min(100)
        private int flushIntervalMs = 500;

        @Min(1000)
        private int timeoutMs = 5000;

        @Min(100)
        private int retryInitialDelayMs = 1000;

        @Min(100)
        private int retryMaxDelayMs = 10000;

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

        public int getRetryInitialDelayMs() {
            return retryInitialDelayMs;
        }

        public void setRetryInitialDelayMs(int retryInitialDelayMs) {
            this.retryInitialDelayMs = retryInitialDelayMs;
        }

        public int getRetryMaxDelayMs() {
            return retryMaxDelayMs;
        }

        public void setRetryMaxDelayMs(int retryMaxDelayMs) {
            this.retryMaxDelayMs = retryMaxDelayMs;
        }
    }

    public static class Reader {
        @NotBlank
        private String mode = "mock";

        @NotBlank
        private String host = "127.0.0.1";

        @Min(1)
        private int port = 5084;

        @NotBlank
        private String name = "impinj-r700";

        private String sdkDir;

        private List<Integer> enabledAntennas = new ArrayList<>(List.of(1, 2, 3, 4));

        private Double txPowerDbM;

        private Double rxSensitivityDbM;

        @Min(0)
        private int session = 2;

        @NotBlank
        private String searchMode = "DualTarget";

        @Min(1000)
        private int keepaliveIntervalMs = 5000;

        @Min(100)
        private int connectRetryInitialDelayMs = 1000;

        @Min(100)
        private int connectRetryMaxDelayMs = 15000;

        @Min(250)
        private int healthCheckIntervalMs = 3000;

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getSdkDir() {
            return sdkDir;
        }

        public void setSdkDir(String sdkDir) {
            this.sdkDir = sdkDir;
        }

        public List<Integer> getEnabledAntennas() {
            return enabledAntennas;
        }

        public void setEnabledAntennas(List<Integer> enabledAntennas) {
            this.enabledAntennas = enabledAntennas;
        }

        public Double getTxPowerDbM() {
            return txPowerDbM;
        }

        public void setTxPowerDbM(Double txPowerDbM) {
            this.txPowerDbM = txPowerDbM;
        }

        public Double getRxSensitivityDbM() {
            return rxSensitivityDbM;
        }

        public void setRxSensitivityDbM(Double rxSensitivityDbM) {
            this.rxSensitivityDbM = rxSensitivityDbM;
        }

        public int getSession() {
            return session;
        }

        public void setSession(int session) {
            this.session = session;
        }

        public String getSearchMode() {
            return searchMode;
        }

        public void setSearchMode(String searchMode) {
            this.searchMode = searchMode;
        }

        public int getKeepaliveIntervalMs() {
            return keepaliveIntervalMs;
        }

        public void setKeepaliveIntervalMs(int keepaliveIntervalMs) {
            this.keepaliveIntervalMs = keepaliveIntervalMs;
        }

        public int getConnectRetryInitialDelayMs() {
            return connectRetryInitialDelayMs;
        }

        public void setConnectRetryInitialDelayMs(int connectRetryInitialDelayMs) {
            this.connectRetryInitialDelayMs = connectRetryInitialDelayMs;
        }

        public int getConnectRetryMaxDelayMs() {
            return connectRetryMaxDelayMs;
        }

        public void setConnectRetryMaxDelayMs(int connectRetryMaxDelayMs) {
            this.connectRetryMaxDelayMs = connectRetryMaxDelayMs;
        }

        public int getHealthCheckIntervalMs() {
            return healthCheckIntervalMs;
        }

        public void setHealthCheckIntervalMs(int healthCheckIntervalMs) {
            this.healthCheckIntervalMs = healthCheckIntervalMs;
        }
    }

    public static class Mock {
        private boolean enabled = true;

        @Min(200)
        private int emitIntervalMs = 2000;

        @NotBlank
        private String tagPrefix = "MOCK-TAG";

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
