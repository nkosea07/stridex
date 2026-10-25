package com.racetracker.service.impl;

import com.racetracker.config.RaceTrackerProperties;
import com.racetracker.model.User;
import com.racetracker.service.AuthEmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.mail.MailException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthEmailServiceImpl implements AuthEmailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final RaceTrackerProperties raceTrackerProperties;

    @Async
    @Override
    public void sendEmailVerification(User user, String token) {
        String link = buildLink(raceTrackerProperties.getEmail().getVerificationPath(), token);
        
        Context context = new Context();
        context.setVariable("firstName", user.getFirstName());
        context.setVariable("verificationLink", link);
        
        String htmlContent = templateEngine.process("email-verification", context);
        
        send(
            user.getEmail(),
            "Verify your Race Tracker account",
            htmlContent,
            true
        );
    }

    @Async
    @Override
    public void sendPasswordReset(User user, String token) {
        String link = buildLink(raceTrackerProperties.getEmail().getResetPasswordPath(), token);
        
        Context context = new Context();
        context.setVariable("firstName", user.getFirstName());
        context.setVariable("resetLink", link);
        
        String htmlContent = templateEngine.process("password-reset", context);
        
        send(
            user.getEmail(),
            "Reset your Race Tracker password",
            htmlContent,
            true
        );
    }

    @Async
    @Override
    public void sendAccountDeletionScheduled(User user, OffsetDateTime scheduledFor) {
        if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
            return;
        }
        Context context = new Context();
        context.setVariable("firstName", user.getFirstName());
        context.setVariable("scheduledDate", scheduledFor == null ? ""
            : scheduledFor.format(DateTimeFormatter.ofPattern("d MMMM yyyy")));

        String htmlContent = templateEngine.process("account-deletion-scheduled", context);
        send(user.getEmail(), "Your Race Tracker account is scheduled for deletion", htmlContent, true);
    }

    @Override
    public void sendAccountDeletionCompletedConfirmed(String email, String firstName) {
        if (email == null || email.isBlank()) {
            throw new com.racetracker.exception.ServiceUnavailableException(
                "Account deletion notice address is unavailable", "ACCOUNT_DELETION_NOTICE_UNAVAILABLE");
        }
        Context context = new Context();
        context.setVariable("firstName", firstName);
        String htmlContent = templateEngine.process("account-deletion-completed", context);
        sendConfirmed(email, "Your Race Tracker account has been deleted", htmlContent);
    }

    private void sendConfirmed(String to, String subject, String body) {
        if (!raceTrackerProperties.getEmail().isEnabled()) {
            throw new com.racetracker.exception.ServiceUnavailableException(
                "Account deletion email delivery is disabled", "ACCOUNT_DELETION_NOTICE_UNAVAILABLE");
        }
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());
            helper.setFrom(raceTrackerProperties.getEmail().getFromAddress());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, true);
            mailSender.send(message);
        } catch (MessagingException | MailException ex) {
            throw new com.racetracker.exception.ServiceUnavailableException(
                "Account deletion email delivery failed", "ACCOUNT_DELETION_NOTICE_UNAVAILABLE");
        }
    }

    private void send(String to, String subject, String body, boolean isHtml) {
        if (!raceTrackerProperties.getEmail().isEnabled()) {
            log.info("Email skipped (disabled). to={}, subject={}", to, subject);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, StandardCharsets.UTF_8.name());

            helper.setFrom(raceTrackerProperties.getEmail().getFromAddress());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, isHtml);

            mailSender.send(message);
            log.info("Email sent successfully to {}", to);
        } catch (MessagingException e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }

    private String buildLink(String path, String token) {
        String base = trimTrailingSlash(raceTrackerProperties.getEmail().getFrontendBaseUrl());
        String normalizedPath = path == null ? "" : path.trim();
        if (!normalizedPath.startsWith("/")) {
            normalizedPath = "/" + normalizedPath;
        }
        return base + normalizedPath + "?token=" + token;
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "http://localhost:5173";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
