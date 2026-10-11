package com.racetracker.rfidreaderimpinj.reader;

import com.racetracker.rfidreaderimpinj.config.ImpinjReaderProperties;
import com.racetracker.rfidreaderimpinj.model.NormalizedRead;

import java.util.function.Consumer;

interface OctaneSdkFacade {

    void connectAndStart(
        ImpinjReaderProperties.Reader readerProperties,
        Consumer<NormalizedRead> readConsumer,
        Runnable keepaliveListener,
        Consumer<String> disconnectListener
    ) throws Exception;

    boolean isConnected();

    void stop() throws Exception;
}
