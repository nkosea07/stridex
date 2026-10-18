package com.racetracker.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private CustomUserDetailsService userDetailsService;

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void refreshToken_doesNotAuthenticateRequest() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, userDetailsService);
        String refreshToken = "refresh-token";
        when(jwtService.hasTokenType(refreshToken, JwtService.TOKEN_TYPE_ACCESS)).thenReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + refreshToken);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        FilterChain chain = (req, res) -> chainCalled.set(true);
        filter.doFilter(request, response, chain);

        assertThat(chainCalled).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(userDetailsService);
    }

    @Test
    void privilegedAccessTokenAuthenticatesWithoutMfaState() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtService, userDetailsService);
        String accessToken = "legacy-access-token";
        when(jwtService.hasTokenType(accessToken, JwtService.TOKEN_TYPE_ACCESS)).thenReturn(true);
        when(jwtService.extractUsername(accessToken)).thenReturn("admin@example.com");
        var userDetails = org.springframework.security.core.userdetails.User.withUsername("admin@example.com")
            .password("hashed")
            .roles("SUPER_ADMIN")
            .build();
        when(userDetailsService.loadUserByUsername("admin@example.com")).thenReturn(userDetails);
        when(jwtService.isTokenValid(accessToken, userDetails)).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + accessToken);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean chainCalled = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> chainCalled.set(true));

        assertThat(chainCalled).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }
}
