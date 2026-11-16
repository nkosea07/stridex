package com.racetracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.racetracker.model.Notification;
import com.racetracker.model.User;
import com.racetracker.repository.NotificationRepository;
import com.racetracker.repository.PushDeviceRepository;
import com.racetracker.repository.UserRepository;
import com.racetracker.service.impl.NotificationDispatcher;
import com.racetracker.service.impl.NotificationServiceImpl;
import com.racetracker.support.TestMessages;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BackendLocalizationTest {

    private final NotificationRepository notifications = mock(NotificationRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final NotificationServiceImpl service = new NotificationServiceImpl(notifications, users,
        mock(PushDeviceRepository.class), mock(NotificationDispatcher.class), new ObjectMapper(),
        mock(NotificationPreferenceService.class), mock(NotificationTemplateService.class),
        TestMessages.messages(), TestMessages.locales());

    private Notification notifyIn(String preferredLocale) {
        UUID userId = UUID.randomUUID();
        when(users.findById(userId)).thenReturn(Optional.of(User.builder().id(userId).email("a@example.com")
            .preferredLocale(preferredLocale).build()));
        when(notifications.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));
        service.notifyLocalized(userId, Notification.NotificationType.RACE_UPDATE, "payment.received",
            new Object[] {"Harare Half"}, Map.of());
        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notifications, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        return saved.getValue();
    }

    @Test
    void notificationsUseTheRecipientsLanguageWithEnglishFallback() {
        Notification shona = notifyIn("sn");
        assertThat(shona.getTitle()).isEqualTo("Mari yagamuchirwa");
        assertThat(shona.getMessage()).isEqualTo("Your payment for Harare Half has been received.");
    }

    @Test
    void unsupportedOrMissingLanguagesFallBackToEnglish() {
        assertThat(notifyIn("xx").getTitle()).isEqualTo("Payment received");
        assertThat(notifyIn(null).getTitle()).isEqualTo("Payment received");
    }

    @Test
    void supportedLocalesAreNormalizedAndOthersRejected() {
        UserLocales locales = TestMessages.locales();
        assertThat(locales.normalize("sn-ZW")).contains("sn");
        assertThat(locales.normalize("FR")).contains("fr");
        assertThat(locales.normalize("de")).isEmpty();
        assertThat(locales.normalize(" ")).isEmpty();
    }

    @Test
    void emailTemplatesRenderFromTheCatalogAndEscapeValues() {
        SpringTemplateEngine engine = new SpringTemplateEngine();
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        engine.setTemplateResolver(resolver);
        engine.setTemplateEngineMessageSource(TestMessages.messageSource());

        Context context = new Context(Locale.ENGLISH);
        context.setVariable("firstName", "Ada");
        context.setVariable("raceName", "<b>Harare</b> Half");
        context.setVariable("bibNumber", "101");
        String html = engine.process("registration-approved", context);

        assertThat(html).contains("Hello Ada,").contains("Your bib number is 101.")
            .contains("&lt;b&gt;Harare&lt;/b&gt; Half").doesNotContain("<b>Harare")
            .doesNotContain("#{").doesNotContain("??");
    }
}
