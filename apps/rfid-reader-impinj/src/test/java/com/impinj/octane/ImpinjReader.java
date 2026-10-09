package com.impinj.octane;

public class ImpinjReader {

    private boolean connected;
    private Object tagReportListener;
    private Object keepaliveListener;
    private Object connectionLostListener;

    public void setTagReportListener(TagReportListener listener) {
        this.tagReportListener = listener;
    }

    public void setKeepaliveListener(KeepaliveListener listener) {
        this.keepaliveListener = listener;
    }

    public void setConnectionLostListener(ConnectionLostListener listener) {
        this.connectionLostListener = listener;
    }

    public void connect(String host) {
        this.connected = true;
    }

    public Settings queryDefaultSettings() {
        return new Settings();
    }

    public void applySettings(Settings settings) {
    }

    public void start() {
        if (keepaliveListener instanceof KeepaliveListener listener) {
            listener.onKeepalive(this);
        }
        if (tagReportListener instanceof TagReportListener listener) {
            listener.onTagReported(this, TagReport.single("E2000016591702081890B31D", (short) 2, -47.2));
        }
    }

    public void stop() {
    }

    public void disconnect() {
        connected = false;
        if (connectionLostListener instanceof ConnectionLostListener listener) {
            listener.onConnectionLost(this);
        }
    }

    public boolean isConnected() {
        return connected;
    }
}
