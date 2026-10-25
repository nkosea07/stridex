package com.racetracker.exception;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.racetracker.dto.error.ErrorResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @AfterEach
    void clearRequestContext() {
        MDC.clear();
    }

    @Test
    void serviceUnavailableResponseUsesSafeMessageAndCorrelationFields() throws Exception {
        String internalMessage = "Redis connection details: redis://user:secret@internal-host";
        MDC.put("requestId", "request-123");

        ResponseEntity<ErrorResponse> response = handler.handleServiceUnavailable(
            new ServiceUnavailableException(internalMessage), request("/api/v1/races"));

        ErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals(503, body.getStatusCode());
        assertEquals("Service Unavailable", body.getError());
        assertEquals("SERVICE_UNAVAILABLE", body.getCode());
        assertEquals("request-123", body.getRequestId());
        assertEquals("The service is temporarily unavailable. Please try again shortly.", body.getMessage());
        assertFalse(body.getMessage().contains(internalMessage));
        assertEquals("/api/v1/races", body.getPath());

        JsonNode serialized = objectMapper.readTree(objectMapper.writeValueAsString(body));
        assertEquals("SERVICE_UNAVAILABLE", serialized.get("code").asText());
        assertEquals("request-123", serialized.get("requestId").asText());
    }

    @Test
    void refundUnavailableResponseExposesStableCodeWithoutInternalDetail() {
        ResponseEntity<ErrorResponse> response = handler.handleServiceUnavailable(
            new ServiceUnavailableException("Provider internals", "REFUND_AUTOMATION_UNAVAILABLE"),
            request("/api/v1/races/r/participants/p/payment/refund"));

        assertNotNull(response.getBody());
        assertEquals("REFUND_AUTOMATION_UNAVAILABLE", response.getBody().getCode());
        assertFalse(response.getBody().getMessage().contains("Provider internals"));
    }

    @Test
    void apiExceptionResponseUsesGenericMessageAndCorrelationFields() {
        String internalMessage = "Storage provider credentials: access-key=secret";
        MDC.put("requestId", "request-789");

        ResponseEntity<ErrorResponse> response = handler.handleApiException(
            new ApiException(internalMessage), request("/api/v1/races/export"));

        ErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("API Error", body.getError());
        assertEquals("API_ERROR", body.getCode());
        assertEquals("request-789", body.getRequestId());
        assertEquals("The request could not be completed.", body.getMessage());
        assertFalse(body.getMessage().contains(internalMessage));
    }

    @Test
    void unexpectedResponseUsesStableCodeAndCorrelationIdWithoutInternalMessage() {
        String internalMessage = "SQL password=super-secret; connection failed";
        MDC.put("requestId", "request-456");

        ResponseEntity<ErrorResponse> response = handler.handleUnexpected(
            new IllegalStateException(internalMessage), request("/api/v1/health"));

        ErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals(500, body.getStatusCode());
        assertEquals("Internal Server Error", body.getError());
        assertEquals("INTERNAL_ERROR", body.getCode());
        assertEquals("request-456", body.getRequestId());
        assertEquals("An unexpected error occurred. Please contact support with request id request-456.", body.getMessage());
        assertFalse(body.getMessage().contains(internalMessage));
        assertEquals("/api/v1/health", body.getPath());
    }

    @Test
    void unexpectedResponseWithoutMdcKeepsRequestIdOptional() {
        MDC.clear();

        ResponseEntity<ErrorResponse> response = handler.handleUnexpected(
            new IllegalStateException("internal failure"), request("/api/v1/health"));

        ErrorResponse body = response.getBody();
        assertNotNull(body);
        assertEquals("INTERNAL_ERROR", body.getCode());
        assertNull(body.getRequestId());
        assertEquals("An unexpected error occurred. Please contact support.", body.getMessage());
    }

    @Test
    void domainExceptionResponsesDoNotExposeInternalMessages() {
        MDC.put("requestId", "request-999");

        ErrorResponse unauthorized = handler.handleUnauthorized(
            new UnauthorizedException("password hash or token detail"), request("/api/v1/auth/login"))
            .getBody();
        ErrorResponse conflict = handler.handleConflict(
            new ConflictException("database constraint detail"), request("/api/v1/races"))
            .getBody();
        ErrorResponse notFound = handler.handleNotFound(
            new ResourceNotFoundException("internal identifier detail"), request("/api/v1/races/1"))
            .getBody();
        ErrorResponse rateLimited = handler.handleTooManyRequests(
            new TooManyRequestsException("redis bucket detail", 10), request("/api/v1/auth/login"))
            .getBody();

        assertEquals("Authentication failed.", unauthorized.getMessage());
        assertEquals("The request conflicts with existing data.", conflict.getMessage());
        assertEquals("The requested resource was not found.", notFound.getMessage());
        assertEquals("Too many requests. Please try again later.", rateLimited.getMessage());
    }

    @Test
    void unauthorizedResponsePreservesStableEmailVerificationCode() {
        ErrorResponse response = handler.handleUnauthorized(
            new UnauthorizedException("Email verification required", "EMAIL_VERIFICATION_REQUIRED"),
            request("/api/v1/auth/login"))
            .getBody();

        assertEquals("EMAIL_VERIFICATION_REQUIRED", response.getCode());
        assertEquals("Authentication failed.", response.getMessage());
    }

    private MockHttpServletRequest request(String requestUri) {
        return new MockHttpServletRequest("GET", requestUri);
    }
}
