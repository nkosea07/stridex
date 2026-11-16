package com.racetracker.rfidreaderimpinj.reader;

import java.lang.reflect.Method;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * Turns the reader's hardware timestamp into the read time. The reader time is the most precise
 * crossing time, but only when the reader clock is synchronised (NTP). If it disagrees with the
 * adapter host by more than the allowed offset, the adapter clock is used and the offset is
 * reported so operators can fix the reader clock before it affects results.
 */
public final class ReaderTimestamps {

    public record Resolved(OffsetDateTime readAt, String source, Long readerClockOffsetMs) {
    }

    private ReaderTimestamps() {
    }

    public static Resolved resolve(Object sdkTimestamp, Instant adapterNow, long maxReaderClockOffsetMs) {
        Instant readerTime = toInstant(sdkTimestamp);
        OffsetDateTime fallback = OffsetDateTime.ofInstant(adapterNow, ZoneOffset.UTC);
        if (readerTime == null) {
            return new Resolved(fallback, "ADAPTER_CLOCK", null);
        }
        long offsetMs = ChronoUnit.MILLIS.between(adapterNow, readerTime);
        if (Math.abs(offsetMs) > Math.max(maxReaderClockOffsetMs, 0)) {
            return new Resolved(fallback, "ADAPTER_CLOCK", offsetMs);
        }
        return new Resolved(OffsetDateTime.ofInstant(readerTime, ZoneOffset.UTC), "READER", offsetMs);
    }

    /** Octane exposes timestamps as microseconds since the Unix epoch (ImpinjTimestamp). */
    static Instant toInstant(Object timestamp) {
        if (timestamp == null) {
            return null;
        }
        if (timestamp instanceof Instant instant) {
            return instant;
        }
        if (timestamp instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        if (timestamp instanceof Date date) {
            return date.toInstant();
        }
        if (timestamp instanceof Number number) {
            return fromMicros(number.longValue());
        }
        Object utc = invokeQuietly(timestamp, "getUtcTimestamp");
        if (utc instanceof Number number) {
            return fromMicros(number.longValue());
        }
        if (utc != null) {
            Instant parsed = parseMicros(utc.toString());
            if (parsed != null) {
                return parsed;
            }
        }
        Object local = invokeQuietly(timestamp, "getLocalDateTime");
        if (local instanceof Date date) {
            return date.toInstant();
        }
        return parseMicros(timestamp.toString());
    }

    private static Instant parseMicros(String value) {
        try {
            return fromMicros(Long.parseLong(value.trim()));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Instant fromMicros(long micros) {
        if (micros <= 0) {
            return null;
        }
        return Instant.ofEpochSecond(Math.floorDiv(micros, 1_000_000L), Math.floorMod(micros, 1_000_000L) * 1_000L);
    }

    private static Object invokeQuietly(Object target, String name) {
        try {
            Method method = target.getClass().getMethod(name);
            return method.invoke(target);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return null;
        }
    }
}
