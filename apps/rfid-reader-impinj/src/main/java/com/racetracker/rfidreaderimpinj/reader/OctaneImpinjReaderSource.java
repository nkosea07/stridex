package com.racetracker.rfidreaderimpinj.reader;

import com.racetracker.rfidreaderimpinj.config.ImpinjReaderProperties;
import com.racetracker.rfidreaderimpinj.service.AdapterRuntimeStatus;
import com.racetracker.rfidreaderimpinj.service.ReadDispatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "adapter.reader.mode", havingValue = "octane")
public class OctaneImpinjReaderSource implements ReaderSource {

    private static final Logger log = LoggerFactory.getLogger(OctaneImpinjReaderSource.class);

    private final ImpinjReaderProperties properties;
    private final ReadDispatchService readDispatchService;
    private final AdapterRuntimeStatus adapterRuntimeStatus;
    private final OctaneSdkFacade octaneSdkFacade;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "impinj-octane-reader");
        thread.setDaemon(true);
        return thread;
    });
    private volatile boolean running;
    private volatile boolean restartRequested;

    public OctaneImpinjReaderSource(
        ImpinjReaderProperties properties,
        ReadDispatchService readDispatchService,
        AdapterRuntimeStatus adapterRuntimeStatus,
        OctaneSdkFacade octaneSdkFacade
    ) {
        this.properties = properties;
        this.readDispatchService = readDispatchService;
        this.adapterRuntimeStatus = adapterRuntimeStatus;
        this.octaneSdkFacade = octaneSdkFacade;
    }

    @Override
    public void start() {
        running = true;
        restartRequested = true;
        adapterRuntimeStatus.markStarting();
        log.info(
            "Octane reader mode selected for {} at {}:{}, attempting Impinj production reader control",
            properties.getReader().getName(),
            properties.getReader().getHost(),
            properties.getReader().getPort()
        );
        worker.submit(this::runLoop);
    }

    @Override
    public void stop() {
        running = false;
        try {
            octaneSdkFacade.stop();
        } catch (Exception error) {
            log.warn("Failed to stop Octane reader cleanly: {}", error.getMessage());
        }
        worker.shutdownNow();
        try {
            worker.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
        adapterRuntimeStatus.markStopped();
        log.info("Octane reader source stopped for {}", properties.getReader().getName());
    }

    private void runLoop() {
        long retryDelayMs = properties.getReader().getConnectRetryInitialDelayMs();
        while (running) {
            try {
                if (restartRequested || !octaneSdkFacade.isConnected()) {
                    adapterRuntimeStatus.markConnecting();
                    octaneSdkFacade.stop();
                    octaneSdkFacade.connectAndStart(
                        properties.getReader(),
                        readDispatchService::accept,
                        adapterRuntimeStatus::recordKeepalive,
                        reason -> {
                            adapterRuntimeStatus.markDisconnected(reason);
                            restartRequested = true;
                        }
                    );
                    adapterRuntimeStatus.markConnected();
                    adapterRuntimeStatus.markInventorying();
                    restartRequested = false;
                    retryDelayMs = properties.getReader().getConnectRetryInitialDelayMs();
                }

                Thread.sleep(properties.getReader().getHealthCheckIntervalMs());
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception error) {
                adapterRuntimeStatus.markReaderError(error.getMessage());
                adapterRuntimeStatus.markDisconnected(error.getMessage());
                restartRequested = true;
                log.warn("Octane reader loop failed for {}: {}", properties.getReader().getName(), error.getMessage());
                sleepQuietly(retryDelayMs);
                retryDelayMs = Math.min(
                    retryDelayMs * 2,
                    properties.getReader().getConnectRetryMaxDelayMs()
                );
            }
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        }
    }
}
