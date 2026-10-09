package com.impinj.octane;

public class Tag {

    private final EpcData epc;
    private final short antennaPortNumber;
    private final double peakRssiInDbm;

    public Tag(String epc, short antennaPortNumber, double peakRssiInDbm) {
        this.epc = new EpcData(epc);
        this.antennaPortNumber = antennaPortNumber;
        this.peakRssiInDbm = peakRssiInDbm;
    }

    public EpcData getEpc() {
        return epc;
    }

    public short getAntennaPortNumber() {
        return antennaPortNumber;
    }

    public double getPeakRssiInDbm() {
        return peakRssiInDbm;
    }
}
