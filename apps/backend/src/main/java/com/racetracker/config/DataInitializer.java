package com.racetracker.config;

import com.racetracker.model.Notification;
import com.racetracker.model.User;
import com.racetracker.repository.NotificationRepository;
import com.racetracker.repository.UserRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {

    @Bean
    @ConditionalOnProperty(
        name = "racetracker.seed.default-admin.enabled",
        havingValue = "true",
        matchIfMissing = false
    )
    CommandLineRunner seedDefaultAdmin(
        UserRepository userRepository,
        NotificationRepository notificationRepository,
        PasswordEncoder passwordEncoder
    ) {
        return args -> {
            String email = "admin@racetracker.com";
            User admin;
            if (userRepository.existsByEmailIgnoreCase(email)) {
                admin = userRepository.findByEmailIgnoreCase(email).orElseThrow();
            } else {
                admin = User.builder()
                    .email(email)
                    .passwordHash(passwordEncoder.encode("admin123"))
                    .firstName("System")
                    .lastName("Admin")
                    .role(User.UserRole.SUPER_ADMIN)
                    .isActive(true)
                    .emailVerified(true)
                    .build();

                admin = userRepository.save(admin);
            }

            if (!notificationRepository.existsByUserIdAndTitle(admin.getId(), "Welcome to Race Tracker")) {
                Notification notification = Notification.builder()
                    .user(admin)
                    .title("Welcome to Race Tracker")
                    .message("Your admin account is ready. Start by creating a race.")
                    .type(Notification.NotificationType.REMINDER)
                    .isRead(false)
                    .build();
                notificationRepository.save(notification);
            }
        };
    }
}
