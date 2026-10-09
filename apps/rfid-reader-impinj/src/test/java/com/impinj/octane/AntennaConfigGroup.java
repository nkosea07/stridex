package com.impinj.octane;

import java.util.HashMap;
import java.util.Map;

public class AntennaConfigGroup {

    private final Map<Short, AntennaConfig> antennas = new HashMap<>();

    public void disableAll() {
        antennas.values().forEach(config -> config.setIsEnabled(false));
    }

    public AntennaConfig getAntenna(short port) {
        return antennas.computeIfAbsent(port, key -> new AntennaConfig());
    }
}
