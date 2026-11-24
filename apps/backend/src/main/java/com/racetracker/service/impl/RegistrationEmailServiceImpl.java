package com.racetracker.service.impl;

import com.racetracker.config.RaceTrackerProperties;
import com.racetracker.model.Participant;
import com.racetracker.model.Race;
import com.racetracker.service.RegistrationEmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.nio.charset.StandardCharsets;

@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationEmailServiceImpl implements RegistrationEmailService {

    private static final java.util.Set<String> CHECK_IN_QR_TEMPLATES = java.util.Set.of("registration-received", "registration-approved");
    private static final String CHECK_IN_QR_CONTENT_ID = "checkInQr";
    private static final int CHECK_IN_QR_SIZE = 240;

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final RaceTrackerProperties raceTrackerProperties;
    private final com.racetracker.service.LocalizedMessages localizedMessages;
    private final com.racetracker.service.UserLocales userLocales;

    @Async
    @Override
    public void sendRegistrationReceived(Participant participant) {
        send(participant, "registration-received", "email.registrationReceived.subject");
    }

    @Async
    @Override
    public void sendRegistrationApproved(Participant participant) {
        send(participant, "registration-approved", "email.registrationApproved.subject");
    }

    @Async
    @Override
    public void sendRegistrationWaitlisted(Participant participant) {
        send(participant, "registration-waitlisted", "email.registrationWaitlisted.subject");
    }

    @Async
    @Override
    public void sendRegistrationRejected(Participant participant) {
        send(participant, "registration-rejected", "email.registrationRejected.subject");
    }

    private void send(Participant participant, String template, String subjectKey) {
        if (participant == null || participant.getEmail() == null || participant.getEmail().isBlank()) {
            return;
        }
        if (!raceTrackerProperties.getEmail().isEnabled()) {
            log.info("Registration email skipped (disabled). to={}, template={}", participant.getEmail(), template);
            return;
        }

        java.util.Locale locale = userLocales.of(participant);
        String raceName = raceName(participant);
        String subject = localizedMessages.get(locale, subjectKey, raceName);
        Context context = new Context(locale);
        context.setVariable("firstName", participant.getFirstName());
        context.setVariable("raceName", raceName);
        context.setVariable("bibNumber", participant.getBibNumber());
        context.setVariable("rejectionReason", participant.getRejectionReason());
        context.setVariable("waitlistPosition", participant.getWaitlistPosition());
        boolean includeCheckInQr = CHECK_IN_QR_TEMPLATES.contains(template)
            && com.racetracker.service.CheckInQr.available(participant);
        context.setVariable("checkInQr", includeCheckInQr);

        try {
            String html = templateEngine.process(template, context);
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, includeCheckInQr, StandardCharsets.UTF_8.name());
            helper.setFrom(raceTrackerProperties.getEmail().getFromAddress());
            helper.setTo(participant.getEmail());
            helper.setSubject(subject);
            helper.setText(html, true);
            if (includeCheckInQr) {
                helper.addInline(CHECK_IN_QR_CONTENT_ID, new org.springframework.core.io.ByteArrayResource(
                    com.racetracker.service.CheckInQr.png(participant.getId(), CHECK_IN_QR_SIZE)), "image/png");
            }
            mailSender.send(message);
            log.info("Registration email sent to {} ({})", participant.getEmail(), template);
        } catch (MessagingException e) {
            log.error("Failed to send registration email to {}: {}", participant.getEmail(), e.getMessage());
        }
    }

    private String raceName(Participant participant) {
        Race race = participant.getRace();
        return race != null && race.getName() != null ? race.getName() : "";
    }
}
