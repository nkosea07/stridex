package com.racetracker.config;

import com.racetracker.model.Notification;
import com.racetracker.model.User;
import com.racetracker.repository.NotificationRepository;
import com.racetracker.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

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
                    .marketingConsent(false)
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

    @Bean(name = "seedLocalUsers")
    @Profile("dev")
    @ConditionalOnProperty(
        name = "racetracker.seed.local-users.enabled",
        havingValue = "true",
        matchIfMissing = false
    )
    CommandLineRunner seedLocalUsers(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        @Value("${racetracker.seed.local-users.race-admin-email:}") String raceAdminEmail,
        @Value("${racetracker.seed.local-users.race-admin-password:}") String raceAdminPassword,
        @Value("${racetracker.seed.local-users.official-email:}") String officialEmail,
        @Value("${racetracker.seed.local-users.official-password:}") String officialPassword,
        @Value("${racetracker.seed.local-users.volunteer-email:}") String volunteerEmail,
        @Value("${racetracker.seed.local-users.volunteer-password:}") String volunteerPassword
    ) {
        return args -> List.of(
            new LocalSeedUser(raceAdminEmail, raceAdminPassword, "Race", "Admin", User.UserRole.RACE_ADMIN),
            new LocalSeedUser(officialEmail, officialPassword, "Race", "Official", User.UserRole.OFFICIAL),
            new LocalSeedUser(volunteerEmail, volunteerPassword, "Race", "Volunteer", User.UserRole.VOLUNTEER)
        ).forEach(user -> ensureLocalUser(userRepository, passwordEncoder, user));
    }

    private void ensureLocalUser(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        LocalSeedUser seedUser
    ) {
        String email = requireConfiguredValue(seedUser.email(), "email");
        String password = requireConfiguredValue(seedUser.password(), "password");

        userRepository.findByEmailIgnoreCase(email).ifPresentOrElse(existing -> {
            if (existing.getRole() != seedUser.role()) {
                throw new IllegalStateException(
                    "Local seed account role mismatch for " + email
                        + ": expected " + seedUser.role() + " but found " + existing.getRole()
                );
            }
        }, () -> userRepository.save(User.builder()
            .email(email)
            .passwordHash(passwordEncoder.encode(password))
            .firstName(seedUser.firstName())
            .lastName(seedUser.lastName())
            .role(seedUser.role())
            .isActive(true)
            .emailVerified(true)
            .marketingConsent(false)
            .build()));
    }

    private String requireConfiguredValue(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Local seed account " + field + " must be configured");
        }
        return value.trim();
    }

    private record LocalSeedUser(
        String email,
        String password,
        String firstName,
        String lastName,
        User.UserRole role
    ) {
    }
}
