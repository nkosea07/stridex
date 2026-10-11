package com.racetracker.rfidreaderdemo.gateway;

import com.racetracker.rfidreaderdemo.model.NormalizedRead;

import java.util.List;

public interface GatewayClient {
    void submitReads(List<NormalizedRead> reads);
}
