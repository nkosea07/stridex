package com.racetracker.security;

import com.racetracker.model.User;
import com.racetracker.model.UserCapability;
import com.racetracker.model.UserCapabilityType;
import com.racetracker.repository.UserCapabilityRepository;
import com.racetracker.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock UserRepository userRepository;
    @Mock UserCapabilityRepository userCapabilityRepository;

    @Test
    void reloadsRoleAndOnlyActiveCapabilityAuthoritiesFromDatabase() {
        UUID userId = UUID.randomUUID();
        User user = User.builder()
            .id(userId)
            .email("runner@example.com")
            .passwordHash("hashed")
            .role(User.UserRole.ATHLETE)
            .isActive(true)
            .emailVerified(true)
            .build();
        UserCapability athlete = UserCapability.builder()
            .user(user)
            .capability(UserCapabilityType.ATHLETE)
            .active(true)
            .build();

        when(userRepository.findByEmailIgnoreCase("runner@example.com")).thenReturn(Optional.of(user));
        when(userCapabilityRepository.findByUserIdAndActiveTrueOrderByCapability(userId))
            .thenReturn(List.of(athlete));

        UserDetails details = new CustomUserDetailsService(userRepository, userCapabilityRepository)
            .loadUserByUsername("runner@example.com");

        assertThat(details.getAuthorities())
            .extracting(authority -> authority.getAuthority())
            .containsExactlyInAnyOrder("ROLE_ATHLETE", "CAP_ATHLETE")
            .doesNotContain("CAP_SPECTATOR");
    }
}
