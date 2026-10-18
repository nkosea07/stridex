package com.racetracker.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;

class ApiSecurityExceptionHandlerTest {

    private final ApiSecurityExceptionHandler handler = new ApiSecurityExceptionHandler(new ObjectMapper().findAndRegisterModules());

    @AfterEach
    void clearRequestId() {
        org.slf4j.MDC.remove("requestId");
    }

    @Test
    void unauthorizedResponseUsesStableShapeAndRequestId() throws Exception {
        org.slf4j.MDC.put("requestId", "req-123");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/races");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.commence(request, response, new BadCredentialsException("secret detail"));

        String body = response.getContentAsString();
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(body).contains("AUTHENTICATION_REQUIRED", "req-123", "Authentication is required")
            .doesNotContain("secret detail");
    }

    @Test
    void forbiddenResponseDoesNotExposeExceptionDetails() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/races");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(request, response, new AccessDeniedException("race secret detail"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("AUTHORIZATION_DENIED")
            .doesNotContain("race secret detail");
    }
}
