package com.racetracker.rfidreaderimpinj.gateway;

import com.racetracker.rfidreaderimpinj.model.NormalizedRead;

import java.util.List;

public interface GatewayClient {
    void submitReads(List<NormalizedRead> reads);
}
