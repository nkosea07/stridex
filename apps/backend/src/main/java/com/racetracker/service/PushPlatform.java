package com.racetracker.service;

public final class PushPlatform {

    public static final String IOS = "ios";

    private PushPlatform() {
    }

    public static boolean isIos(String platform) {
        return platform != null && IOS.equalsIgnoreCase(platform.trim());
    }
}
