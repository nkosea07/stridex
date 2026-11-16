package com.racetracker.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RfidTagValuesTest {

    @Test
    void hexEpcsMatchWhateverFormatTheReaderOrSpreadsheetUses() {
        String canonical = "E2003411B802011803954C45";
        assertThat(RfidTagValues.canonical("e2003411b802011803954c45")).isEqualTo(canonical);
        assertThat(RfidTagValues.canonical(" E200 3411 B802 0118 0395 4C45 ")).isEqualTo(canonical);
        assertThat(RfidTagValues.canonical("E2-00-34-11-B8-02-01-18-03-95-4C-45")).isEqualTo(canonical);
        assertThat(RfidTagValues.canonical("E2:00:34:11:B8:02:01:18:03:95:4C:45")).isEqualTo(canonical);
    }

    @Test
    void nonHexLabelsAreOnlyTrimmed() {
        assertThat(RfidTagValues.canonical(" TAG-001 ")).isEqualTo("TAG-001");
        assertThat(RfidTagValues.canonical("   ")).isNull();
        assertThat(RfidTagValues.canonical(null)).isNull();
    }
}
