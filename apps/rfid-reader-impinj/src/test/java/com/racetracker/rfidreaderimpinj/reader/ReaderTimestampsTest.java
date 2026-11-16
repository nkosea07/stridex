package com.racetracker.rfidreaderimpinj.reader;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ReaderTimestampsTest {

    private final Instant adapterNow = Instant.parse("2026-11-15T09:12:30.500Z");

    public static final class FakeImpinjTimestamp {
        private final String micros;

        FakeImpinjTimestamp(String micros) {
            this.micros = micros;
        }

        public String getUtcTimestamp() {
            return micros;
        }
    }

    @Test
    void readerHardwareTimeIsUsedWithMicrosecondPrecisionWhenItsClockIsInSync() {
        long micros = adapterNow.minusMillis(40).getEpochSecond() * 1_000_000L + 460_123L;

        ReaderTimestamps.Resolved resolved = ReaderTimestamps.resolve(
            new FakeImpinjTimestamp(Long.toString(micros)), adapterNow, 2000);

        assertThat(resolved.source()).isEqualTo("READER");
        assertThat(resolved.readAt().toInstant()).isEqualTo(Instant.parse("2026-11-15T09:12:30.460123Z"));
        assertThat(resolved.readerClockOffsetMs()).isEqualTo(-40);
    }

    @Test
    void readerClockOffsetIsRoundedToTheNearestMillisecondInBothDirections() {
        long ahead = adapterNow.getEpochSecond() * 1_000_000L + 500_000L + 1_499L;
        long behind = adapterNow.getEpochSecond() * 1_000_000L + 500_000L - 1_501L;

        assertThat(ReaderTimestamps.resolve(ahead, adapterNow, 2000).readerClockOffsetMs()).isEqualTo(1);
        assertThat(ReaderTimestamps.resolve(behind, adapterNow, 2000).readerClockOffsetMs()).isEqualTo(-2);
    }

    @Test
    void readerWithAWrongClockFallsBackToTheAdapterClockAndReportsTheOffset() {
        long micros = adapterNow.minusSeconds(3600).toEpochMilli() * 1000L;

        ReaderTimestamps.Resolved resolved = ReaderTimestamps.resolve(micros, adapterNow, 2000);

        assertThat(resolved.source()).isEqualTo("ADAPTER_CLOCK");
        assertThat(resolved.readAt().toInstant()).isEqualTo(adapterNow);
        assertThat(resolved.readerClockOffsetMs()).isEqualTo(-3_600_000L);
    }

    @Test
    void missingTimestampUsesTheAdapterClock() {
        ReaderTimestamps.Resolved resolved = ReaderTimestamps.resolve(null, adapterNow, 2000);

        assertThat(resolved.source()).isEqualTo("ADAPTER_CLOCK");
        assertThat(resolved.readerClockOffsetMs()).isNull();
    }
}
