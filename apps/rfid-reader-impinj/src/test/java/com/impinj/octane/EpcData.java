package com.impinj.octane;

public class EpcData {

    private final String value;

    public EpcData(String value) {
        this.value = value;
    }

    public String toHexString() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
