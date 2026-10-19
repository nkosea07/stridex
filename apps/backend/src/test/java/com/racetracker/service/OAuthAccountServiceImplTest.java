package com.racetracker.service;

import com.racetracker.dto.auth.AuthResponse;
import com.racetracker.dto.auth.UserDto;
import com.racetracker.exception.UnauthorizedException;
import com.racetracker.mapper.UserMapper;
import com.racetracker.model.User;
import com.racetracker.repository.AuthTokenRepository;
import com.racetracker.repository.UserRepository;
import com.racetracker.repository.UserCapabilityRepository;
import com.racetracker.security.JwtService;
import com.racetracker.service.impl.OAuthAccountServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuthAccountServiceImplTest {

    @Mock OAuthIdentityVerifier identityVerifier;
    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;
    @Mock UserMapper userMapper;
    @Mock AuthTokenRepository authTokenRepository;
    @Mock UserCapabilityRepository userCapabilityRepository;

    private OAuthAccountServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OAuthAccountServiceImpl(identityVerifier, userRepository, passwordEncoder,
            jwtService, userMapper, authTokenRepository, userCapabilityRepository);
    }

    private void stubTokenIssuance() {
        lenient().when(jwtService.generateRefreshToken(any(User.class))).thenReturn("refresh-token");
        lenient().when(jwtService.generateAccessToken(any(User.class))).thenReturn("access-token");
        lenient().when(jwtService.extractExpiration("refresh-token")).thenReturn(OffsetDateTime.now().plusDays(7));
        lenient().when(userMapper.toDto(any(User.class))).thenReturn(new UserDto());
        lenient().when(authTokenRepository.findByUserIdAndTokenTypeAndConsumedAtIsNull(any(), any()))
            .thenReturn(java.util.List.of());
    }

    @Test
    void createsAthleteAndIssuesSessionForNewGoogleUser() {
        when(identityVerifier.verifyGoogle("id-token"))
            .thenReturn(new OAuthIdentity("google", "sub-1", "runner@example.com", true, "Ada", "Runner"));
        when(userRepository.findByEmailIgnoreCase("runner@example.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("hash");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User user = inv.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });
        stubTokenIssuance();

        AuthResponse response = service.authenticateWithGoogle("id-token");

        assertThat(response.getToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(response.getRequiresEmailVerification()).isFalse();
    }

    @Test
    void rejectsUnverifiedGoogleEmail() {
        when(identityVerifier.verifyGoogle("id-token"))
            .thenReturn(new OAuthIdentity("google", "sub-1", "runner@example.com", false, "Ada", "Runner"));

        assertThatThrownBy(() -> service.authenticateWithGoogle("id-token"))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessageContaining("not verified");
    }

    @Test
    void rejectsPrivilegedAccounts() {
        User admin = User.builder().id(UUID.randomUUID()).email("admin@example.com")
            .role(User.UserRole.RACE_ADMIN).isActive(true).emailVerified(true).build();
        when(identityVerifier.verifyGoogle("id-token"))
            .thenReturn(new OAuthIdentity("google", "sub-1", "admin@example.com", true, "Ad", "Min"));
        when(userRepository.findByEmailIgnoreCase("admin@example.com")).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.authenticateWithGoogle("id-token"))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessageContaining("Privileged");
    }
}
