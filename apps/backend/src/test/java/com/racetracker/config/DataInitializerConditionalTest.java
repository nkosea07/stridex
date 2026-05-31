package com.racetracker.config;

import com.racetracker.repository.NotificationRepository;
import com.racetracker.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DataInitializerConditionalTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(DataInitializer.class, TestBeans.class);

    @Test
    void seedRunner_isCreatedWhenEnabled() {
        contextRunner
            .withPropertyValues("racetracker.seed.default-admin.enabled=true")
            .run(context -> assertThat(context).hasBean("seedDefaultAdmin"));
    }

    @Test
    void seedRunner_isNotCreatedWhenDisabled() {
        contextRunner
            .withPropertyValues("racetracker.seed.default-admin.enabled=false")
            .run(context -> assertThat(context).doesNotHaveBean("seedDefaultAdmin"));
    }

    @Configuration
    static class TestBeans {
        @Bean
        UserRepository userRepository() {
            return mock(UserRepository.class);
        }

        @Bean
        NotificationRepository notificationRepository() {
            return mock(NotificationRepository.class);
        }

        @Bean
        PasswordEncoder passwordEncoder() {
            return mock(PasswordEncoder.class);
        }
    }
}
