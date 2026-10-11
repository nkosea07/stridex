package com.impinj.octane;

public class Settings {

    private final ReportConfig report = new ReportConfig();
    private final KeepaliveConfig keepalives = new KeepaliveConfig();
    private final AntennaConfigGroup antennas = new AntennaConfigGroup();
    private SearchMode searchMode;
    private int session;

    public ReportConfig getReport() {
        return report;
    }

    public KeepaliveConfig getKeepalives() {
        return keepalives;
    }

    public AntennaConfigGroup getAntennas() {
        return antennas;
    }

    public void setSearchMode(SearchMode searchMode) {
        this.searchMode = searchMode;
    }

    public SearchMode getSearchMode() {
        return searchMode;
    }

    public void setSession(int session) {
        this.session = session;
    }

    public int getSession() {
        return session;
    }
}
