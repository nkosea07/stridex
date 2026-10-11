package com.racetracker.service;

import com.racetracker.dto.auth.AuthResponse;
import com.racetracker.exception.UnauthorizedException;
import com.racetracker.mapper.UserMapper;
import com.racetracker.model.AuthToken;
import com.racetracker.model.User;
import com.racetracker.repository.UserRepository;
import com.racetracker.security.JwtService;
import com.racetracker.service.AuthEmailService;
import com.racetracker.service.impl.AuthServiceImpl;
import com.racetracker.service.impl.MfaAuthenticationServiceImpl;
import com.racetracker.repository.AuthTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplRefreshTokenTest {

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

    @Mock
    private MfaAuthenticationServiceImpl mfaAuthenticationService;

    @Mock
    private AuditEventService auditEventService;

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    void refreshToken_rejectsAccessToken() {
        String token = "access-token";
        when(jwtService.hasTokenType(token, JwtService.TOKEN_TYPE_REFRESH)).thenReturn(false);

        assertThrows(UnauthorizedException.class, () -> authService.refreshToken(token));
        verifyNoInteractions(userRepository);
    }

    @Test
    void refreshToken_rotatesStoredRefreshToken() {
        User user = baseUser();
        String refreshToken = "refresh-token";
        String rotatedRefreshToken = "rotated-refresh-token";

        AuthToken storedToken = AuthToken.builder()
            .id(UUID.randomUUID())
            .user(user)
            .token(refreshToken)
            .tokenType(AuthToken.TokenType.REFRESH)
            .expiresAt(OffsetDateTime.now().plusMinutes(30))
            .build();

        when(jwtService.hasTokenType(refreshToken, JwtService.TOKEN_TYPE_REFRESH)).thenReturn(true);
        when(authTokenRepository.findByTokenAndTokenTypeAndConsumedAtIsNull(refreshToken, AuthToken.TokenType.REFRESH))
            .thenReturn(Optional.of(storedToken));
        when(authTokenRepository.findByUserIdAndTokenTypeAndConsumedAtIsNull(user.getId(), AuthToken.TokenType.REFRESH))
            .thenReturn(List.of());
        when(jwtService.isTokenValid(eq(refreshToken), any(org.springframework.security.core.userdetails.UserDetails.class)))
            .thenReturn(true);
        when(jwtService.generateAccessToken(user)).thenReturn("access-token");
        when(jwtService.generateRefreshToken(user)).thenReturn(rotatedRefreshToken);
        when(jwtService.extractExpiration(rotatedRefreshToken)).thenReturn(OffsetDateTime.now().plusDays(7));
        when(userMapper.toDto(user)).thenReturn(new com.racetracker.dto.auth.UserDto());

        AuthResponse response = authService.refreshToken(refreshToken);

        assertEquals("access-token", response.getToken());
        assertEquals(rotatedRefreshToken, response.getRefreshToken());
        verify(authTokenRepository).save(storedToken);
        verify(authTokenRepository).save(argThat(token ->
            token.getTokenType() == AuthToken.TokenType.REFRESH
                && rotatedRefreshToken.equals(token.getToken())
                && user.equals(token.getUser())
        ));
    }

    @Test
    void refreshToken_rejectsUnknownStoredToken() {
        String refreshToken = "refresh-token";

        when(jwtService.hasTokenType(refreshToken, JwtService.TOKEN_TYPE_REFRESH)).thenReturn(true);
        when(authTokenRepository.findByTokenAndTokenTypeAndConsumedAtIsNull(refreshToken, AuthToken.TokenType.REFRESH))
            .thenReturn(Optional.empty());

        assertThrows(UnauthorizedException.class, () -> authService.refreshToken(refreshToken));
        verifyNoInteractions(userRepository);
    }

    private User baseUser() {
        return User.builder()
            .id(UUID.randomUUID())
            .email("athlete@example.com")
            .passwordHash("hashed")
            .firstName("Athlete")
            .lastName("Runner")
            .role(User.UserRole.ATHLETE)
            .isActive(true)
            .emailVerified(true)
            .build();
    }
}
