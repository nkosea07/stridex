package com.racetracker.service;

import com.racetracker.dto.auth.ForgotPasswordRequest;
import com.racetracker.dto.auth.LoginRequest;
import com.racetracker.dto.auth.RegisterRequest;
import com.racetracker.dto.auth.AuthResponse;
import com.racetracker.exception.TooManyRequestsException;
import com.racetracker.exception.UnauthorizedException;
import com.racetracker.exception.ApiException;
import com.racetracker.mapper.UserMapper;
import com.racetracker.model.AuthToken;
import com.racetracker.model.User;
import com.racetracker.model.UserCapability;
import com.racetracker.repository.AuthTokenRepository;
import com.racetracker.repository.ParticipantRepository;
import com.racetracker.repository.UserCapabilityRepository;
import com.racetracker.repository.UserRepository;
import com.racetracker.security.JwtService;
import com.racetracker.service.impl.AuthServiceImpl;
import com.racetracker.service.impl.MfaAuthenticationServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplSecurityFlowTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

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
    private UserCapabilityRepository userCapabilityRepository;

    @Mock
    private ParticipantRepository participantRepository;

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
    void loginForPrivilegedUserReturnsSessionWithoutMfaChallenge() {
        LoginRequest request = new LoginRequest();
        request.setEmail("admin@example.com");
        request.setPassword("password123");
        User user = baseUser();
        user.setRole(User.UserRole.SUPER_ADMIN);
        user.setEmail("admin@example.com");
        when(userRepository.findByEmailIgnoreCase("admin@example.com")).thenReturn(Optional.of(user));
        when(userMapper.toDto(user)).thenReturn(new com.racetracker.dto.auth.UserDto());
        when(authTokenRepository.findByUserIdAndTokenTypeAndConsumedAtIsNull(user.getId(), AuthToken.TokenType.REFRESH))
            .thenReturn(List.of());
        when(jwtService.generateAccessToken(user)).thenReturn("access-token");
        when(jwtService.generateRefreshToken(user)).thenReturn("refresh-token");
        when(jwtService.extractExpiration("refresh-token")).thenReturn(OffsetDateTime.now().plusDays(7));

        AuthResponse response = authService.login(request);

        assertThat(response.getToken()).isEqualTo("access-token");
        assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
        assertThat(response.getMfaRequired()).isNull();
        assertThat(response.getMfaChallengeToken()).isNull();
        verifyNoInteractions(mfaAuthenticationService);
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
        request.setPrivacyPolicyAccepted(true);
        request.setPrivacyPolicyVersion("2026-10-11");

        when(userRepository.existsByEmailIgnoreCase("newuser@example.com")).thenReturn(false);
        when(passwordEncoder.encode("12345678")).thenReturn("hashed");

        User saved = baseUser();
        saved.setId(UUID.randomUUID());
        saved.setEmail("newuser@example.com");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(authTokenRepository.findByUserIdAndTokenTypeAndConsumedAtIsNull(any(), eq(AuthToken.TokenType.EMAIL_VERIFICATION)))
            .thenReturn(List.of());

        when(userMapper.toDto(any(User.class))).thenReturn(new com.racetracker.dto.auth.UserDto());

        AuthResponse response = authService.register(request);

        assertNull(response.getToken());
        assertNull(response.getRefreshToken());
        assertTrue(Boolean.TRUE.equals(response.getRequiresEmailVerification()));
        verify(authTokenRepository, atLeastOnce()).save(any(AuthToken.class));
        verify(authEmailService).sendEmailVerification(any(User.class), anyString());
    }

    @Test
    void register_recordsPrivacyPolicyAcceptance() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("privacy@example.com");
        request.setPassword("12345678");
        request.setFirstName("Pri");
        request.setLastName("Vacy");
        request.setPrivacyPolicyAccepted(true);
        request.setPrivacyPolicyVersion("2026-10-11");

        when(userRepository.existsByEmailIgnoreCase("privacy@example.com")).thenReturn(false);
        when(passwordEncoder.encode("12345678")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User user = inv.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });
        when(authTokenRepository.findByUserIdAndTokenTypeAndConsumedAtIsNull(any(), any())).thenReturn(List.of());
        when(userMapper.toDto(any(User.class))).thenReturn(new com.racetracker.dto.auth.UserDto());

        authService.register(request);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getPrivacyPolicyVersion()).isEqualTo("2026-10-11");
        assertThat(captor.getValue().getPrivacyPolicyAcceptedAt()).isNotNull();
    }

    @Test
    void registerRejectsMissingPrivacyConsent() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("new@example.com");
        request.setPassword("12345678");
        request.setFirstName("New");
        request.setLastName("User");

        assertThatThrownBy(() -> authService.register(request))
            .isInstanceOf(ApiException.class)
            .extracting("code").isEqualTo("PRIVACY_CONSENT_REQUIRED");
        verify(userRepository, never()).save(any());
    }

    @Test
    void registerRejectsStalePrivacyPolicyVersion() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("new@example.com");
        request.setPassword("12345678");
        request.setFirstName("New");
        request.setLastName("User");
        request.setPrivacyPolicyAccepted(true);
        request.setPrivacyPolicyVersion("old");

        assertThatThrownBy(() -> authService.register(request))
            .isInstanceOf(ApiException.class)
            .extracting("code").isEqualTo("PRIVACY_POLICY_VERSION_MISMATCH");
        verify(userRepository, never()).save(any());
    }

    @Test
    void register_defaultsToAthleteCapabilityWhenExperienceIsOmitted() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("default@example.com");
        request.setPassword("12345678");
        request.setFirstName("Default");
        request.setLastName("Athlete");
        request.setPrivacyPolicyAccepted(true);
        request.setPrivacyPolicyVersion("2026-10-11");

        when(userRepository.existsByEmailIgnoreCase("default@example.com")).thenReturn(false);
        when(passwordEncoder.encode("12345678")).thenReturn("hashed");
        User saved = baseUser();
        saved.setEmail("default@example.com");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });
        when(authTokenRepository.findByUserIdAndTokenTypeAndConsumedAtIsNull(any(), any())).thenReturn(List.of());
        when(userMapper.toDto(any(User.class))).thenReturn(new com.racetracker.dto.auth.UserDto());

        authService.register(request);

        ArgumentCaptor<com.racetracker.model.UserCapability> captor =
            ArgumentCaptor.forClass(com.racetracker.model.UserCapability.class);
        verify(userCapabilityRepository).save(captor.capture());
        assertThat(captor.getValue().getCapability()).isEqualTo(com.racetracker.model.UserCapabilityType.ATHLETE);
        assertThat(captor.getValue().getUser().getPreferredCapability())
            .isEqualTo(com.racetracker.model.UserCapabilityType.ATHLETE);
    }

    @Test
    void register_supportsSpectatorOnlyExperienceSet() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("spectator@example.com");
        request.setPassword("12345678");
        request.setFirstName("Spectator");
        request.setLastName("User");
        request.setExperiences(Set.of(com.racetracker.model.UserCapabilityType.SPECTATOR));
        request.setPrivacyPolicyAccepted(true);
        request.setPrivacyPolicyVersion("2026-10-11");

        when(userRepository.existsByEmailIgnoreCase("spectator@example.com")).thenReturn(false);
        when(passwordEncoder.encode("12345678")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });
        when(authTokenRepository.findByUserIdAndTokenTypeAndConsumedAtIsNull(any(), any())).thenReturn(List.of());
        when(userMapper.toDto(any(User.class))).thenReturn(new com.racetracker.dto.auth.UserDto());

        authService.register(request);

        ArgumentCaptor<UserCapability> capabilityCaptor = ArgumentCaptor.forClass(UserCapability.class);
        verify(userCapabilityRepository).save(capabilityCaptor.capture());
        assertThat(capabilityCaptor.getValue().getCapability())
            .isEqualTo(com.racetracker.model.UserCapabilityType.SPECTATOR);
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getPreferredCapability())
            .isEqualTo(com.racetracker.model.UserCapabilityType.SPECTATOR);
    }

    @Test
    void register_supportsBothExperienceSetWithAthletePreferredByDefault() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("both@example.com");
        request.setPassword("12345678");
        request.setFirstName("Dual");
        request.setLastName("User");
        request.setExperiences(EnumSet.of(
            com.racetracker.model.UserCapabilityType.ATHLETE,
            com.racetracker.model.UserCapabilityType.SPECTATOR));
        request.setPrivacyPolicyAccepted(true);
        request.setPrivacyPolicyVersion("2026-10-11");

        when(userRepository.existsByEmailIgnoreCase("both@example.com")).thenReturn(false);
        when(passwordEncoder.encode("12345678")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(UUID.randomUUID());
            return user;
        });
        when(authTokenRepository.findByUserIdAndTokenTypeAndConsumedAtIsNull(any(), any())).thenReturn(List.of());
        when(userMapper.toDto(any(User.class))).thenReturn(new com.racetracker.dto.auth.UserDto());

        authService.register(request);

        ArgumentCaptor<UserCapability> capabilityCaptor = ArgumentCaptor.forClass(UserCapability.class);
        verify(userCapabilityRepository, times(2)).save(capabilityCaptor.capture());
        assertThat(capabilityCaptor.getAllValues())
            .extracting(UserCapability::getCapability)
            .containsExactlyInAnyOrder(
                com.racetracker.model.UserCapabilityType.ATHLETE,
                com.racetracker.model.UserCapabilityType.SPECTATOR);
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getPreferredCapability())
            .isEqualTo(com.racetracker.model.UserCapabilityType.ATHLETE);
    }

    @Test
    void updateCapabilitiesPersistsSelectionAndPreferredCapability() {
        User user = baseUser();
        when(userRepository.findByEmailIgnoreCaseForUpdate("athlete@example.com")).thenReturn(Optional.of(user));
        when(userCapabilityRepository.findByUserIdAndCapability(any(), any())).thenReturn(Optional.empty());
        when(userCapabilityRepository.save(any(com.racetracker.model.UserCapability.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.save(user)).thenReturn(user);
        when(userMapper.toDto(user)).thenReturn(new com.racetracker.dto.auth.UserDto());
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("athlete@example.com", null, List.of()));

        com.racetracker.dto.auth.UpdateCapabilitiesRequest request = new com.racetracker.dto.auth.UpdateCapabilitiesRequest();
        request.setCapabilities(EnumSet.of(com.racetracker.model.UserCapabilityType.ATHLETE,
            com.racetracker.model.UserCapabilityType.SPECTATOR));
        request.setPreferredCapability(com.racetracker.model.UserCapabilityType.SPECTATOR);

        authService.updateCapabilities(request);

        assertThat(user.getPreferredCapability()).isEqualTo(com.racetracker.model.UserCapabilityType.SPECTATOR);
        verify(userCapabilityRepository, times(2)).save(any(com.racetracker.model.UserCapability.class));
        verify(auditEventService).recordInCurrentTransaction(eq("CAPABILITIES_UPDATED"), eq(user.getId()),
            eq("USER"), eq(user.getId().toString()), eq("SUCCESS"), anyMap());
    }

    @Test
    void updateCapabilitiesBlocksAthleteRemovalForActiveRegistration() {
        User user = baseUser();
        when(userRepository.findByEmailIgnoreCaseForUpdate("athlete@example.com")).thenReturn(Optional.of(user));
        when(participantRepository.existsByUserIdAndStatusIn(any(), any())).thenReturn(true);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("athlete@example.com", null, List.of()));

        com.racetracker.dto.auth.UpdateCapabilitiesRequest request = new com.racetracker.dto.auth.UpdateCapabilitiesRequest();
        request.setCapabilities(Set.of(com.racetracker.model.UserCapabilityType.SPECTATOR));
        request.setPreferredCapability(com.racetracker.model.UserCapabilityType.SPECTATOR);

        assertThatThrownBy(() -> authService.updateCapabilities(request))
            .isInstanceOf(com.racetracker.exception.ConflictException.class)
            .hasMessageContaining("active registration");
        verifyNoInteractions(userCapabilityRepository);
    }

    @Test
    void updateCapabilitiesRejectsEmptySelectionWithStableCode() {
        User user = baseUser();
        when(userRepository.findByEmailIgnoreCaseForUpdate("athlete@example.com")).thenReturn(Optional.of(user));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("athlete@example.com", null, List.of()));

        com.racetracker.dto.auth.UpdateCapabilitiesRequest request =
            new com.racetracker.dto.auth.UpdateCapabilitiesRequest();
        request.setCapabilities(Set.of());
        request.setPreferredCapability(com.racetracker.model.UserCapabilityType.ATHLETE);

        assertThatThrownBy(() -> authService.updateCapabilities(request))
            .isInstanceOf(com.racetracker.exception.ApiException.class)
            .hasFieldOrPropertyWithValue("code", "CAPABILITIES_REQUIRED")
            .hasMessageContaining("At least one capability");
    }

    @Test
    void updateCapabilitiesRejectsMissingPreferredSelectionWithStableCode() {
        User user = baseUser();
        when(userRepository.findByEmailIgnoreCaseForUpdate("athlete@example.com")).thenReturn(Optional.of(user));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("athlete@example.com", null, List.of()));

        com.racetracker.dto.auth.UpdateCapabilitiesRequest request =
            new com.racetracker.dto.auth.UpdateCapabilitiesRequest();
        request.setCapabilities(Set.of(com.racetracker.model.UserCapabilityType.ATHLETE));

        assertThatThrownBy(() -> authService.updateCapabilities(request))
            .isInstanceOf(com.racetracker.exception.ApiException.class)
            .hasFieldOrPropertyWithValue("code", "PREFERRED_CAPABILITY_REQUIRED")
            .hasMessageContaining("Preferred capability");
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
