package com.racetracker.service;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * One canonical form for RFID tag values so a tag matches regardless of how a reader,
 * spreadsheet, or operator formats it. Hex EPCs ("e200 3411-b802", "E2:00:34") are stored
 * upper-case without separators; other values are only trimmed so non-hex test labels are
 * kept as entered.
 */
public final class RfidTagValues {

    private static final Pattern SEPARATORS = Pattern.compile("[\\s:-]");
    private static final Pattern HEX = Pattern.compile("^[0-9A-Fa-f]+$");

    private RfidTagValues() {
    }

    public static String canonical(String rawValue) {
        if (rawValue == null) {
            return null;
        }
        String trimmed = rawValue.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        String stripped = SEPARATORS.matcher(trimmed).replaceAll("");
        if (!stripped.isEmpty() && HEX.matcher(stripped).matches()) {
            return stripped.toUpperCase(Locale.ROOT);
        }
        return trimmed;
    }
}
