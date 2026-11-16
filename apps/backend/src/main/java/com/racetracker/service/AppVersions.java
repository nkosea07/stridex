package com.racetracker.service;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Compares mobile app versions of the form MAJOR[.MINOR[.PATCH]] (a pre-release or build suffix
 * is ignored). A version that cannot be read is never treated as outdated, so a malformed header
 * cannot lock users out.
 */
public final class AppVersions {

    private static final Pattern VERSION = Pattern.compile("^v?(\\d{1,6})(?:\\.(\\d{1,6}))?(?:\\.(\\d{1,6}))?(?:[-+].*)?$");

    private AppVersions() {
    }

    public static Optional<int[]> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        Matcher matcher = VERSION.matcher(raw.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return Optional.of(new int[] {
            Integer.parseInt(matcher.group(1)),
            matcher.group(2) == null ? 0 : Integer.parseInt(matcher.group(2)),
            matcher.group(3) == null ? 0 : Integer.parseInt(matcher.group(3)),
        });
    }

    public static boolean isBelow(String version, String minimum) {
        Optional<int[]> current = parse(version);
        Optional<int[]> floor = parse(minimum);
        if (current.isEmpty() || floor.isEmpty()) {
            return false;
        }
        for (int index = 0; index < 3; index++) {
            if (current.get()[index] != floor.get()[index]) {
                return current.get()[index] < floor.get()[index];
            }
        }
        return false;
    }
}
