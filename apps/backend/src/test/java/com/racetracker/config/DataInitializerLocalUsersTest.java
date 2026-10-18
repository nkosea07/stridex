package com.racetracker.config;

import com.racetracker.model.User;
import com.racetracker.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DataInitializerLocalUsersTest {

    private static final String RACE_ADMIN_EMAIL = "raceadmin.local@racetracker.test";
    private static final String RACE_ADMIN_PASSWORD = "RaceAdminTest123!";
    private static final String OFFICIAL_EMAIL = "official.local@racetracker.test";
    private static final String OFFICIAL_PASSWORD = "OfficialTest123!";
    private static final String VOLUNTEER_EMAIL = "volunteer.local@racetracker.test";
    private static final String VOLUNTEER_PASSWORD = "VolunteerTest123!";

    @Test
    void seedLocalUsers_createsAllAccountsWithEncodedPasswordsAndRoleFlags() throws Exception {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        Map<String, User> users = inMemoryUsers(userRepository);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-password");

        CommandLineRunner runner = runner(userRepository, passwordEncoder);
        runner.run();

        assertThat(users).containsOnlyKeys(
            RACE_ADMIN_EMAIL,
            OFFICIAL_EMAIL,
            VOLUNTEER_EMAIL
        );
        assertAccount(users.get(RACE_ADMIN_EMAIL), User.UserRole.RACE_ADMIN);
        assertAccount(users.get(OFFICIAL_EMAIL), User.UserRole.OFFICIAL);
        assertAccount(users.get(VOLUNTEER_EMAIL), User.UserRole.VOLUNTEER);
        verify(passwordEncoder).encode(RACE_ADMIN_PASSWORD);
        verify(passwordEncoder).encode(OFFICIAL_PASSWORD);
        verify(passwordEncoder).encode(VOLUNTEER_PASSWORD);
        verify(userRepository, times(3)).save(any(User.class));
    }

    @Test
    void seedLocalUsers_isIdempotentAndLeavesExistingSameRoleAccountsUnchanged() throws Exception {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        Map<String, User> users = inMemoryUsers(userRepository);
        User existing = User.builder()
            .email(RACE_ADMIN_EMAIL)
            .passwordHash("existing-hash")
            .firstName("Existing")
            .lastName("Admin")
            .role(User.UserRole.RACE_ADMIN)
            .isActive(false)
            .emailVerified(false)
            .build();
        users.put(RACE_ADMIN_EMAIL, existing);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded-password");

        CommandLineRunner runner = runner(userRepository, passwordEncoder);
        runner.run();
        runner.run();

        assertThat(users.get(RACE_ADMIN_EMAIL)).isSameAs(existing);
        assertThat(existing.getPasswordHash()).isEqualTo("existing-hash");
        assertThat(existing.getIsActive()).isFalse();
        assertThat(existing.getEmailVerified()).isFalse();
        verify(userRepository, times(2)).save(any(User.class));
        verify(passwordEncoder, times(2)).encode(anyString());
    }

    @Test
    void seedLocalUsers_rejectsReservedEmailWithWrongRole() {
        UserRepository userRepository = mock(UserRepository.class);
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        Map<String, User> users = inMemoryUsers(userRepository);
        users.put(RACE_ADMIN_EMAIL, User.builder()
            .email(RACE_ADMIN_EMAIL)
            .passwordHash("existing-hash")
            .firstName("Existing")
            .lastName("Official")
            .role(User.UserRole.OFFICIAL)
            .isActive(true)
            .emailVerified(true)
            .build());

        assertThatThrownBy(() -> runner(userRepository, passwordEncoder)
            .run())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(RACE_ADMIN_EMAIL)
            .hasMessageContaining("expected RACE_ADMIN")
            .hasMessageContaining("found OFFICIAL");
    }

    private CommandLineRunner runner(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        return new DataInitializer().seedLocalUsers(
            userRepository,
            passwordEncoder,
            RACE_ADMIN_EMAIL,
            RACE_ADMIN_PASSWORD,
            OFFICIAL_EMAIL,
            OFFICIAL_PASSWORD,
            VOLUNTEER_EMAIL,
            VOLUNTEER_PASSWORD
        );
    }

    private Map<String, User> inMemoryUsers(UserRepository userRepository) {
        Map<String, User> users = new HashMap<>();
        when(userRepository.findByEmailIgnoreCase(anyString())).thenAnswer(invocation ->
            Optional.ofNullable(users.get(invocation.getArgument(0, String.class).toLowerCase(Locale.ROOT)))
        );
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0, User.class);
            users.put(saved.getEmail().toLowerCase(Locale.ROOT), saved);
            return saved;
        });
        return users;
    }

    private void assertAccount(User user, User.UserRole role) {
        assertThat(user.getRole()).isEqualTo(role);
        assertThat(user.getPasswordHash()).isEqualTo("encoded-password");
        assertThat(user.getIsActive()).isTrue();
        assertThat(user.getEmailVerified()).isTrue();
        assertThat(user.getMarketingConsent()).isFalse();
    }
}
