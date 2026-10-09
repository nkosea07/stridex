package com.racetracker.rfidreaderimpinj.reader;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

@Component
public class ReaderLifecycle {

    private final ReaderSource readerSource;

    public ReaderLifecycle(ReaderSource readerSource) {
        this.readerSource = readerSource;
    }

    @PostConstruct
    void start() {
        readerSource.start();
    }

    @PreDestroy
    void stop() {
        readerSource.stop();
    }
}
