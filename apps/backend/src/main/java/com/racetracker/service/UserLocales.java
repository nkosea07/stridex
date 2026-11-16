package com.racetracker.service;

import com.racetracker.config.RaceTrackerProperties;
import com.racetracker.model.Participant;
import com.racetracker.model.User;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

/**
 * Resolves the language for text sent to a user (emails, notifications). Only locales listed in
 * racetracker.i18n.supported-locales (mirroring the API contract) are used; anything else falls
 * back to English.
 */
@Component
public class UserLocales {

    public static final Locale DEFAULT = Locale.ENGLISH;

    private final RaceTrackerProperties properties;

    public UserLocales(RaceTrackerProperties properties) {
        this.properties = properties;
    }

    public Optional<String> normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String tag = raw.trim().replace('_', '-');
        String language = Locale.forLanguageTag(tag).getLanguage();
        return properties.getI18n().getSupportedLocales().stream()
            .map(value -> value.trim().toLowerCase(Locale.ROOT))
            .filter(supported -> supported.equals(language) || supported.equalsIgnoreCase(tag))
            .findFirst();
    }

    public String fromCurrentRequest() {
        return normalize(LocaleContextHolder.getLocale().toLanguageTag()).orElse(null);
    }

    public Locale of(User user) {
        return user == null ? DEFAULT : normalize(user.getPreferredLocale()).map(Locale::forLanguageTag).orElse(DEFAULT);
    }

    public Locale of(Participant participant) {
        return participant == null ? DEFAULT : of(participant.getUser());
    }
}
