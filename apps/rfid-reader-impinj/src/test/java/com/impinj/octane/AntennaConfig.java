package com.impinj.octane;

public class AntennaConfig {

    private boolean enabled;
    private Double txPowerInDbm;
    private Double rxSensitivityInDbm;

    public void setIsEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setTxPowerInDbm(Double txPowerInDbm) {
        this.txPowerInDbm = txPowerInDbm;
    }

    public void setRxSensitivityInDbm(Double rxSensitivityInDbm) {
        this.rxSensitivityInDbm = rxSensitivityInDbm;
    }
}
