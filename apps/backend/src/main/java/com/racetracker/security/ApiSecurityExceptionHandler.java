package com.racetracker.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.racetracker.dto.error.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.LocalDateTime;

@Component
public class ApiSecurityExceptionHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public ApiSecurityExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
        HttpServletRequest request,
        HttpServletResponse response,
        AuthenticationException exception
    ) throws IOException {
        write(response, request, HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "Authentication is required");
    }

    @Override
    public void handle(
        HttpServletRequest request,
        HttpServletResponse response,
        AccessDeniedException exception
    ) throws IOException {
        write(response, request, HttpStatus.FORBIDDEN, "AUTHORIZATION_DENIED", "You do not have permission to perform this action");
    }

    private void write(
        HttpServletResponse response,
        HttpServletRequest request,
        HttpStatus status,
        String code,
        String message
    ) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(status)
            .statusCode(status.value())
            .error(status.getReasonPhrase())
            .message(message)
            .code(code)
            .requestId(MDC.get("requestId"))
            .path(request.getRequestURI())
            .build());
    }
}
