package com.impinj.octane;

public class ReportConfig {

    private boolean includeAntennaPortNumber;
    private boolean includePeakRssi;
    private ReportMode mode;

    public void setIncludeAntennaPortNumber(boolean includeAntennaPortNumber) {
        this.includeAntennaPortNumber = includeAntennaPortNumber;
    }

    public void setIncludePeakRssi(boolean includePeakRssi) {
        this.includePeakRssi = includePeakRssi;
    }

    public void setMode(ReportMode mode) {
        this.mode = mode;
    }
}
