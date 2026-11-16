package com.racetracker.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AppVersionsTest {

    @Test
    void comparesNumericallyPartByPart() {
        assertThat(AppVersions.isBelow("1.9.0", "1.10.0")).isTrue();
        assertThat(AppVersions.isBelow("1.10.0", "1.9.9")).isFalse();
        assertThat(AppVersions.isBelow("2", "1.99.99")).isFalse();
        assertThat(AppVersions.isBelow("1.4", "1.4.0")).isFalse();
        assertThat(AppVersions.isBelow("1.4.0-beta.2", "1.4.1")).isTrue();
        assertThat(AppVersions.isBelow("v1.4.1", "1.4.1")).isFalse();
    }

    @Test
    void unreadableVersionsAreNeverTreatedAsOutdated() {
        assertThat(AppVersions.isBelow("banana", "9.0.0")).isFalse();
        assertThat(AppVersions.isBelow("", "9.0.0")).isFalse();
        assertThat(AppVersions.isBelow("1.0.0", "")).isFalse();
    }
}
