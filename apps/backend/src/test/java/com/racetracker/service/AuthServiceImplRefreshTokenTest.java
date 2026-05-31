package com.racetracker.service;

import com.racetracker.exception.UnauthorizedException;
import com.racetracker.mapper.UserMapper;
import com.racetracker.repository.UserRepository;
import com.racetracker.security.JwtService;
import com.racetracker.service.AuthEmailService;
import com.racetracker.service.impl.AuthServiceImpl;
import com.racetracker.repository.AuthTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
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

    @InjectMocks
    private AuthServiceImpl authService;

    @Test
    void refreshToken_rejectsAccessToken() {
        String token = "access-token";
        when(jwtService.hasTokenType(token, JwtService.TOKEN_TYPE_REFRESH)).thenReturn(false);

        assertThrows(UnauthorizedException.class, () -> authService.refreshToken(token));
        verifyNoInteractions(userRepository);
    }
}
