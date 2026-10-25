package com.racetracker.service;

import com.racetracker.config.RaceTrackerProperties;
import com.racetracker.exception.ServiceUnavailableException;
import com.racetracker.service.impl.AuthEmailServiceImpl;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthEmailServiceImplTest {

    @Mock JavaMailSender mailSender;
    private final TemplateEngine templateEngine = new SpringTemplateEngine();

    AuthEmailServiceImplTest() {
        templateEngine.setTemplateResolver(new StringTemplateResolver());
    }

    @Test
    void disabledMailDoesNotAcknowledgeDeletionNotice() {
        RaceTrackerProperties properties = new RaceTrackerProperties();
        properties.getEmail().setEnabled(false);
        assertThatThrownBy(() -> new AuthEmailServiceImpl(mailSender, templateEngine, properties)
            .sendAccountDeletionCompletedConfirmed("runner@example.com", "Ada"))
            .isInstanceOf(ServiceUnavailableException.class)
            .extracting("code").isEqualTo("ACCOUNT_DELETION_NOTICE_UNAVAILABLE");
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void mailProviderFailureDoesNotAcknowledgeDeletionNotice() {
        RaceTrackerProperties properties = new RaceTrackerProperties();
        properties.getEmail().setEnabled(true);
        properties.getEmail().setFromAddress("noreply@example.com");
        when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        doThrow(new MailSendException("unavailable")).when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> new AuthEmailServiceImpl(mailSender, templateEngine, properties)
            .sendAccountDeletionCompletedConfirmed("runner@example.com", "Ada"))
            .isInstanceOf(ServiceUnavailableException.class)
            .extracting("code").isEqualTo("ACCOUNT_DELETION_NOTICE_UNAVAILABLE");
    }
}
