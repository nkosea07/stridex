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
import static org.mockito.Mockito.never;
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

    @Mock
    private com.racetracker.security.AdminMfaPolicy adminMfaPolicy;

    @Mock
    private com.racetracker.service.UserLocales userLocales;

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
    void adminRefreshTokenWithoutVerifiedMfaIsNotRenewed() {
        User admin = baseUser();
        admin.setRole(User.UserRole.SUPER_ADMIN);
        String refreshToken = "legacy-admin-refresh";
        AuthToken storedToken = storedToken(admin, refreshToken, UUID.randomUUID(), null);
        when(jwtService.hasTokenType(refreshToken, JwtService.TOKEN_TYPE_REFRESH)).thenReturn(true);
        when(authTokenRepository.findByTokenAndTokenType(refreshToken, AuthToken.TokenType.REFRESH))
            .thenReturn(Optional.of(storedToken));
        when(jwtService.isTokenValid(eq(refreshToken), any(org.springframework.security.core.userdetails.UserDetails.class)))
            .thenReturn(true);
        when(jwtService.isMfaVerified(refreshToken)).thenReturn(false);
        when(adminMfaPolicy.requiresMfa(User.UserRole.SUPER_ADMIN)).thenReturn(true);

        org.junit.jupiter.api.Assertions.assertThrows(com.racetracker.exception.UnauthorizedException.class,
            () -> authService.refreshToken(refreshToken));
        verify(authTokenRepository).consumeIfActive(eq(storedToken.getId()), any(OffsetDateTime.class));
        verify(jwtService, never()).generateAccessToken(any());
    }

    @Test
    void refreshToken_rotatesStoredRefreshTokenWithinItsSessionFamily() {
        User user = baseUser();
        String refreshToken = "refresh-token";
        String rotatedRefreshToken = "rotated-refresh-token";
        UUID familyId = UUID.randomUUID();
        AuthToken storedToken = storedToken(user, refreshToken, familyId, null);

        when(jwtService.hasTokenType(refreshToken, JwtService.TOKEN_TYPE_REFRESH)).thenReturn(true);
        when(authTokenRepository.findByTokenAndTokenType(refreshToken, AuthToken.TokenType.REFRESH))
            .thenReturn(Optional.of(storedToken));
        when(jwtService.isTokenValid(eq(refreshToken), any(org.springframework.security.core.userdetails.UserDetails.class)))
            .thenReturn(true);
        when(authTokenRepository.consumeIfActive(eq(storedToken.getId()), any(OffsetDateTime.class))).thenReturn(1);
        when(jwtService.generateAccessToken(user)).thenReturn("access-token");
        when(jwtService.generateRefreshToken(user)).thenReturn(rotatedRefreshToken);
        when(jwtService.extractExpiration(rotatedRefreshToken)).thenReturn(OffsetDateTime.now().plusDays(7));
        when(userMapper.toDto(user)).thenReturn(new com.racetracker.dto.auth.UserDto());

        AuthResponse response = authService.refreshToken(refreshToken);

        assertEquals("access-token", response.getToken());
        assertEquals(rotatedRefreshToken, response.getRefreshToken());
        verify(authTokenRepository).save(argThat(token ->
            token.getTokenType() == AuthToken.TokenType.REFRESH
                && rotatedRefreshToken.equals(token.getToken())
                && user.equals(token.getUser())
                && familyId.equals(token.getFamilyId())
        ));
        verify(authTokenRepository, never()).findByUserIdAndTokenTypeAndConsumedAtIsNull(any(), any());
    }

    @Test
    void refreshToken_rejectsTheLoserOfAConcurrentRotation() {
        User user = baseUser();
        String refreshToken = "refresh-token";
        AuthToken storedToken = storedToken(user, refreshToken, UUID.randomUUID(), null);

        when(jwtService.hasTokenType(refreshToken, JwtService.TOKEN_TYPE_REFRESH)).thenReturn(true);
        when(authTokenRepository.findByTokenAndTokenType(refreshToken, AuthToken.TokenType.REFRESH))
            .thenReturn(Optional.of(storedToken));
        when(jwtService.isTokenValid(eq(refreshToken), any(org.springframework.security.core.userdetails.UserDetails.class)))
            .thenReturn(true);
        when(authTokenRepository.consumeIfActive(eq(storedToken.getId()), any(OffsetDateTime.class))).thenReturn(0);

        assertThrows(UnauthorizedException.class, () -> authService.refreshToken(refreshToken));
        verify(jwtService, never()).generateRefreshToken(any(User.class));
    }

    @Test
    void refreshToken_reuseShortlyAfterRotationIsRejectedWithoutRevokingTheSession() {
        String refreshToken = "refresh-token";
        AuthToken storedToken = storedToken(baseUser(), refreshToken, UUID.randomUUID(), OffsetDateTime.now().minusSeconds(5));

        when(jwtService.hasTokenType(refreshToken, JwtService.TOKEN_TYPE_REFRESH)).thenReturn(true);
        when(authTokenRepository.findByTokenAndTokenType(refreshToken, AuthToken.TokenType.REFRESH))
            .thenReturn(Optional.of(storedToken));

        assertThrows(UnauthorizedException.class, () -> authService.refreshToken(refreshToken));
        verify(authTokenRepository, never()).revokeFamily(any(), any(), any());
        verifyNoInteractions(auditEventService);
    }

    @Test
    void refreshToken_laterReuseRevokesOnlyThatSessionFamilyAndIsAudited() {
        String refreshToken = "refresh-token";
        UUID familyId = UUID.randomUUID();
        AuthToken storedToken = storedToken(baseUser(), refreshToken, familyId, OffsetDateTime.now().minusMinutes(10));

        when(jwtService.hasTokenType(refreshToken, JwtService.TOKEN_TYPE_REFRESH)).thenReturn(true);
        when(authTokenRepository.findByTokenAndTokenType(refreshToken, AuthToken.TokenType.REFRESH))
            .thenReturn(Optional.of(storedToken));
        when(authTokenRepository.revokeFamily(eq(familyId), eq(AuthToken.TokenType.REFRESH), any(OffsetDateTime.class)))
            .thenReturn(1);

        assertThrows(UnauthorizedException.class, () -> authService.refreshToken(refreshToken));
        verify(authTokenRepository).revokeFamily(eq(familyId), eq(AuthToken.TokenType.REFRESH), any(OffsetDateTime.class));
        verify(authTokenRepository, never()).findByUserIdAndTokenTypeAndConsumedAtIsNull(any(), any());
        verify(auditEventService).record(eq("REFRESH_TOKEN_REUSE"), any(), eq("AUTH_TOKEN_FAMILY"), eq(familyId.toString()),
            eq("DENIED"), any());
    }

    @Test
    void refreshToken_rejectsUnknownStoredToken() {
        String refreshToken = "refresh-token";

        when(jwtService.hasTokenType(refreshToken, JwtService.TOKEN_TYPE_REFRESH)).thenReturn(true);
        when(authTokenRepository.findByTokenAndTokenType(refreshToken, AuthToken.TokenType.REFRESH))
            .thenReturn(Optional.empty());

        assertThrows(UnauthorizedException.class, () -> authService.refreshToken(refreshToken));
        verifyNoInteractions(userRepository);
    }

    @Test
    void refreshTokenKeepsReuseRevocationWhenRejecting() throws NoSuchMethodException {
        org.springframework.transaction.annotation.Transactional transactional = AuthServiceImpl.class
            .getMethod("refreshToken", String.class)
            .getAnnotation(org.springframework.transaction.annotation.Transactional.class);

        org.assertj.core.api.Assertions.assertThat(transactional.noRollbackFor()).contains(UnauthorizedException.class);
    }

    private AuthToken storedToken(User user, String token, UUID familyId, OffsetDateTime consumedAt) {
        return AuthToken.builder()
            .id(UUID.randomUUID())
            .user(user)
            .token(token)
            .tokenType(AuthToken.TokenType.REFRESH)
            .expiresAt(OffsetDateTime.now().plusMinutes(30))
            .consumedAt(consumedAt)
            .familyId(familyId)
            .build();
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
