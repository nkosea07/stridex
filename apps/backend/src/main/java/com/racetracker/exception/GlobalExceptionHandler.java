package com.racetracker.exception;

import com.racetracker.dto.error.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String API_ERROR_MESSAGE = "The request could not be completed.";
    private static final String SERVICE_UNAVAILABLE_MESSAGE =
        "The service is temporarily unavailable. Please try again shortly.";
    private static final String UNAUTHORIZED_MESSAGE = "Authentication failed.";
    private static final String CONFLICT_MESSAGE = "The request conflicts with existing data.";
    private static final String NOT_FOUND_MESSAGE = "The requested resource was not found.";
    private static final String RATE_LIMIT_MESSAGE = "Too many requests. Please try again later.";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.toList());

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST)
                .statusCode(HttpStatus.BAD_REQUEST.value())
                .error("Validation Error")
                .message("Input validation failed")
                .code("VALIDATION_ERROR")
                .requestId(requestId())
                .path(request.getRequestURI())
                .details(errors)
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.FORBIDDEN)
                .statusCode(HttpStatus.FORBIDDEN.value())
                .error("Forbidden")
                .message("You do not have permission to perform this action")
                .code("FORBIDDEN")
                .requestId(requestId())
                .path(request.getRequestURI())
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler({BadCredentialsException.class, UnauthorizedException.class})
    public ResponseEntity<ErrorResponse> handleUnauthorized(Exception ex, HttpServletRequest request) {
        String code = ex instanceof UnauthorizedException unauthorized
            ? unauthorized.getCode()
            : "UNAUTHORIZED";
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.UNAUTHORIZED)
                .statusCode(HttpStatus.UNAUTHORIZED.value())
                .error("Unauthorized")
                .message(UNAUTHORIZED_MESSAGE)
                .code(code)
                .requestId(requestId())
                .path(request.getRequestURI())
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.UNAUTHORIZED);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex, HttpServletRequest request) {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.CONFLICT)
                .statusCode(HttpStatus.CONFLICT.value())
                .error("Conflict")
                .message(CONFLICT_MESSAGE)
                .code("CONFLICT")
                .requestId(requestId())
                .path(request.getRequestURI())
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.CONFLICT);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.NOT_FOUND)
                .statusCode(HttpStatus.NOT_FOUND.value())
                .error("Not Found")
                .message(NOT_FOUND_MESSAGE)
                .code("NOT_FOUND")
                .requestId(requestId())
                .path(request.getRequestURI())
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex, HttpServletRequest request) {
        String requestId = requestId();
        log.warn("API exception for {} (requestId={})", request.getRequestURI(), requestId, ex);
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST)
                .statusCode(HttpStatus.BAD_REQUEST.value())
                .error("API Error")
                .message(API_ERROR_MESSAGE)
                .code("API_ERROR")
                .requestId(requestId)
                .path(request.getRequestURI())
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ErrorResponse> handleTooManyRequests(TooManyRequestsException ex, HttpServletRequest request) {
        ErrorResponse errorResponse = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.TOO_MANY_REQUESTS)
            .statusCode(HttpStatus.TOO_MANY_REQUESTS.value())
            .error("Too Many Requests")
            .message(RATE_LIMIT_MESSAGE)
            .code("TOO_MANY_REQUESTS")
            .requestId(requestId())
            .path(request.getRequestURI())
            .build();
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
            .body(errorResponse);
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleServiceUnavailable(ServiceUnavailableException ex, HttpServletRequest request) {
        String requestId = requestId();
        log.error("Service unavailable for {} (requestId={})", request.getRequestURI(), requestId, ex);
        ErrorResponse errorResponse = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.SERVICE_UNAVAILABLE)
            .statusCode(HttpStatus.SERVICE_UNAVAILABLE.value())
            .error("Service Unavailable")
            .message(SERVICE_UNAVAILABLE_MESSAGE)
            .code("SERVICE_UNAVAILABLE")
            .requestId(requestId)
            .path(request.getRequestURI())
            .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParams(MissingServletRequestParameterException ex, HttpServletRequest request) {
        String error = ex.getParameterName() + " parameter is missing";
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.BAD_REQUEST)
                .statusCode(HttpStatus.BAD_REQUEST.value())
                .error("Missing Parameter")
                .message(error)
                .code("MISSING_PARAMETER")
                .requestId(requestId())
                .path(request.getRequestURI())
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex, HttpServletRequest request) {
        String requestId = MDC.get("requestId");
        log.error("Unhandled exception for {} (requestId={})", request.getRequestURI(), requestId, ex);
        String message = requestId != null
                ? "An unexpected error occurred. Please contact support with request id " + requestId + "."
                : "An unexpected error occurred. Please contact support.";
        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(LocalDateTime.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .statusCode(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error("Internal Server Error")
                .message(message)
                .code("INTERNAL_ERROR")
                .requestId(requestId)
                .path(request.getRequestURI())
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private String requestId() {
        return MDC.get("requestId");
    }
}
