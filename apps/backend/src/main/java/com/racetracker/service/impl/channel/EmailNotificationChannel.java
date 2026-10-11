package com.racetracker.service.impl.channel;

import com.racetracker.config.RaceTrackerProperties;
import com.racetracker.model.NotificationDelivery;
import com.racetracker.notification.NotificationMessage;
import com.racetracker.service.NotificationChannel;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
public class EmailNotificationChannel implements NotificationChannel {

    private final JavaMailSender mailSender;
    private final RaceTrackerProperties properties;

    public EmailNotificationChannel(JavaMailSender mailSender, RaceTrackerProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public NotificationDelivery.Channel channel() {
        return NotificationDelivery.Channel.EMAIL;
    }

    @Override
    public boolean isEnabled() {
        return properties.getEmail() != null && properties.getEmail().isEnabled();
    }

    @Override
    public void send(NotificationMessage message) {
        String recipient = message.recipientEmail();
        if (recipient == null || recipient.isBlank()) {
            return;
        }
        SimpleMailMessage mail = new SimpleMailMessage();
        String from = properties.getEmail().getFromAddress();
        if (from != null && !from.isBlank()) {
            mail.setFrom(from);
        }
        mail.setTo(recipient);
        mail.setSubject(message.title());
        mail.setText(message.body());
        mailSender.send(mail);
    }
}
