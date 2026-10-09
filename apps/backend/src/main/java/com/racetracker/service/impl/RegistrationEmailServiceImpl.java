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

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final RaceTrackerProperties raceTrackerProperties;

    @Async
    @Override
    public void sendRegistrationReceived(Participant participant) {
        send(participant, "registration-received",
            "We received your registration for " + raceName(participant));
    }

    @Async
    @Override
    public void sendRegistrationApproved(Participant participant) {
        send(participant, "registration-approved",
            "Your registration for " + raceName(participant) + " is confirmed");
    }

    @Async
    @Override
    public void sendRegistrationWaitlisted(Participant participant) {
        send(participant, "registration-waitlisted",
            "You are on the waitlist for " + raceName(participant));
    }

    @Async
    @Override
    public void sendRegistrationRejected(Participant participant) {
        send(participant, "registration-rejected",
            "Update on your registration for " + raceName(participant));
    }

    private void send(Participant participant, String template, String subject) {
        if (participant == null || participant.getEmail() == null || participant.getEmail().isBlank()) {
            return;
        }
        if (!raceTrackerProperties.getEmail().isEnabled()) {
            log.info("Registration email skipped (disabled). to={}, template={}", participant.getEmail(), template);
            return;
        }

        Race race = participant.getRace();
        Context context = new Context();
        context.setVariable("firstName", participant.getFirstName());
        context.setVariable("raceName", race != null ? race.getName() : "your race");
        context.setVariable("bibNumber", participant.getBibNumber());
        context.setVariable("rejectionReason", participant.getRejectionReason());
        context.setVariable("waitlistPosition", participant.getWaitlistPosition());

        try {
            String html = templateEngine.process(template, context);
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
            helper.setFrom(raceTrackerProperties.getEmail().getFromAddress());
            helper.setTo(participant.getEmail());
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
            log.info("Registration email sent to {} ({})", participant.getEmail(), template);
        } catch (MessagingException e) {
            log.error("Failed to send registration email to {}: {}", participant.getEmail(), e.getMessage());
        }
    }

    private String raceName(Participant participant) {
        Race race = participant.getRace();
        return race != null ? race.getName() : "your race";
    }
}
