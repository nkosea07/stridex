package com.racetracker.service;

import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/**
 * Thin wrapper over Spring's {@link MessageSource} for localized, parameterized messages.
 * Falls back to a supplied default when a key is missing so callers never break on a
 * missing translation.
 */
@Component
public class LocalizedMessages {

    private final MessageSource messageSource;

    public LocalizedMessages(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public String get(String code, Object... args) {
        return messageSource.getMessage(code, args, LocaleContextHolder.getLocale());
    }

    public String getOrDefault(String code, String fallback, Object... args) {
        try {
            return get(code, args);
        } catch (NoSuchMessageException ex) {
            return fallback;
        }
    }
}
