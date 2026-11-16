package com.racetracker.support;

import com.racetracker.config.RaceTrackerProperties;
import com.racetracker.service.LocalizedMessages;
import com.racetracker.service.UserLocales;
import org.springframework.context.support.ResourceBundleMessageSource;

import java.util.List;

public final class TestMessages {

    private TestMessages() {
    }

    public static ResourceBundleMessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        source.setFallbackToSystemLocale(false);
        return source;
    }

    public static LocalizedMessages messages() {
        return new LocalizedMessages(messageSource());
    }

    public static UserLocales locales() {
        RaceTrackerProperties properties = new RaceTrackerProperties();
        properties.getI18n().setSupportedLocales(List.of("en", "sn", "nd", "af", "zu", "fr", "pt", "sw"));
        return new UserLocales(properties);
    }
}
