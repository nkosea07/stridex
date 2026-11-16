package com.racetracker.service;

import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Thin wrapper over Spring's {@link MessageSource} for localized, parameterized messages. Text
 * sent to someone else (emails, notifications) must use the recipient's locale via
 * {@link #get(Locale, String, Object...)}; a key missing in that locale falls back to the base
 * (English) catalog.
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

    public String get(Locale locale, String code, Object... args) {
        return messageSource.getMessage(code, args, locale == null ? UserLocales.DEFAULT : locale);
    }

    public String getOrDefault(String code, String fallback, Object... args) {
        try {
            return get(code, args);
        } catch (NoSuchMessageException ex) {
            return fallback;
        }
    }
}
