package com.racetracker.service;

import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;

import static org.assertj.core.api.Assertions.assertThat;

class LocalizedMessagesTest {

    private LocalizedMessages messages() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        return new LocalizedMessages(source);
    }

    @Test
    void resolvesKnownKeysWithArguments() {
        assertThat(messages().get("notification.emergency.title")).isEqualTo("Emergency alert");
        assertThat(messages().get("notification.emergency.body", "Ada")).contains("Ada");
    }

    @Test
    void fallsBackWhenKeyIsMissing() {
        assertThat(messages().getOrDefault("missing.key", "fallback")).isEqualTo("fallback");
    }
}
