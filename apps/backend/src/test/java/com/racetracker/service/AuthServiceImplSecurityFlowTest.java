package com.racetracker.service;

import com.racetracker.dto.auth.ForgotPasswordRequest;
import com.racetracker.dto.auth.LoginRequest;
import com.racetracker.dto.auth.RegisterRequest;
import com.racetracker.dto.auth.AuthResponse;
import com.racetracker.exception.TooManyRequestsException;
import com.racetracker.exception.UnauthorizedException;
import com.racetracker.mapper.UserMapper;
import com.racetracker.model.AuthToken;
import com.racetracker.model.User;
import com.racetracker.repository.AuthTokenRepository;
import com.racetracker.repository.UserRepository;
import com.racetracker.security.JwtService;
import com.racetracker.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplSecurityFlowTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @Mock
    private UserMapper userMapper;

    @Mock
    private AuthTokenRepository authTokenRepository;

    @Mock
    private AuthEmailService authEmailService;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    void login_rejectsWhenAccountLocked() {
        LoginRequest request = new LoginRequest();
        request.setEmail("athlete@example.com");
        request.setPassword("bad-password");

        User user = baseUser();
        user.setLockoutUntil(OffsetDateTime.now().plusMinutes(5));

        when(userRepository.findByEmailIgnoreCase("athlete@example.com")).thenReturn(Optional.of(user));

        assertThrows(TooManyRequestsException.class, () -> authService.login(request));
        verifyNoInteractions(authenticationManager);
    }

    @Test
    void login_tracksFailedAttemptOnInvalidCredentials() {
        LoginRequest request = new LoginRequest();
        request.setEmail("athlete@example.com");
        request.setPassword("bad-password");

        User user = baseUser();
        when(userRepository.findByEmailIgnoreCase("athlete@example.com")).thenReturn(Optional.of(user));
        doThrow(new BadCredentialsException("Bad credentials"))
            .when(authenticationManager)
            .authenticate(any(UsernamePasswordAuthenticationToken.class));

        assertThrows(UnauthorizedException.class, () -> authService.login(request));
        verify(userRepository, atLeastOnce()).save(any(User.class));
    }

    @Test
    void login_rejectsUnverifiedUserBeforeIssuingSession() {
        LoginRequest request = new LoginRequest();
        request.setEmail("athlete@example.com");
        request.setPassword("password123");

        User user = baseUser();
        user.setEmailVerified(false);
        when(userRepository.findByEmailIgnoreCase("athlete@example.com")).thenReturn(Optional.of(user));

        assertThrows(UnauthorizedException.class, () -> authService.login(request));
        verifyNoInteractions(authenticationManager);
    }

    @Test
    void forgotPassword_doesNotFailForUnknownEmail() {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("unknown@example.com");

        when(userRepository.findByEmailIgnoreCase("unknown@example.com")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> authService.forgotPassword(request));
        verifyNoInteractions(authEmailService);
    }

    @Test
    void register_issuesEmailVerificationToken() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("newuser@example.com");
        request.setPassword("12345678");
        request.setFirstName("New");
        request.setLastName("User");

        when(userRepository.existsByEmailIgnoreCase("newuser@example.com")).thenReturn(false);
        when(passwordEncoder.encode("12345678")).thenReturn("hashed");

        User saved = baseUser();
        saved.setId(UUID.randomUUID());
        saved.setEmail("newuser@example.com");
        when(userRepository.save(any(User.class))).thenReturn(saved);
        when(authTokenRepository.findByUserIdAndTokenTypeAndConsumedAtIsNull(saved.getId(), AuthToken.TokenType.EMAIL_VERIFICATION))
            .thenReturn(List.of());

        when(userMapper.toDto(saved)).thenReturn(new com.racetracker.dto.auth.UserDto());

        AuthResponse response = authService.register(request);

        assertNull(response.getToken());
        assertNull(response.getRefreshToken());
        assertTrue(Boolean.TRUE.equals(response.getRequiresEmailVerification()));
        verify(authTokenRepository, atLeastOnce()).save(any(AuthToken.class));
        verify(authEmailService).sendEmailVerification(eq(saved), anyString());
    }

    @Test
    void resetPassword_revokesOutstandingRefreshTokens() {
        User user = baseUser();
        AuthToken passwordResetToken = AuthToken.builder()
            .id(UUID.randomUUID())
            .user(user)
            .token("reset-token")
            .tokenType(AuthToken.TokenType.PASSWORD_RESET)
            .expiresAt(OffsetDateTime.now().plusMinutes(10))
            .build();
        AuthToken refreshToken = AuthToken.builder()
            .id(UUID.randomUUID())
            .user(user)
            .token("refresh-token")
            .tokenType(AuthToken.TokenType.REFRESH)
            .expiresAt(OffsetDateTime.now().plusDays(1))
            .build();

        com.racetracker.dto.auth.ResetPasswordRequest request = new com.racetracker.dto.auth.ResetPasswordRequest();
        request.setToken("reset-token");
        request.setNewPassword("new-password");

        when(authTokenRepository.findByToken("reset-token")).thenReturn(Optional.of(passwordResetToken));
        when(authTokenRepository.findByUserIdAndTokenTypeAndConsumedAtIsNull(user.getId(), AuthToken.TokenType.REFRESH))
            .thenReturn(List.of(refreshToken));
        when(authTokenRepository.save(passwordResetToken)).thenReturn(passwordResetToken);

        assertDoesNotThrow(() -> authService.resetPassword(request));
        verify(authTokenRepository).save(passwordResetToken);
        verify(authTokenRepository).saveAll(anyList());
    }

    private User baseUser() {
        return User.builder()
            .id(UUID.randomUUID())
            .email("athlete@example.com")
            .passwordHash("hashed")
            .firstName("Athlete")
            .lastName("One")
            .role(User.UserRole.ATHLETE)
            .isActive(true)
            .emailVerified(true)
            .failedLoginAttempts(0)
            .build();
    }
}
