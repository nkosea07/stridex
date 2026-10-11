package com.impinj.octane;

public class KeepaliveConfig {

    private boolean enable;
    private int periodInMs;

    public void setEnable(boolean enable) {
        this.enable = enable;
    }

    public void setPeriodInMs(int periodInMs) {
        this.periodInMs = periodInMs;
    }
}
