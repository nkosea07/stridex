package com.racetracker.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.racetracker.config.RaceTrackerProperties;
import com.racetracker.dto.error.ErrorResponse;
import com.racetracker.service.AppVersions;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Refuses API calls from a mobile app older than the configured minimum with 426 and the stable
 * code APP_UPDATE_REQUIRED, so the app can show an update screen instead of failing on a changed
 * API. Only requests that send X-App-Version are checked; browsers and integrations never send it.
 */
public class MinimumAppVersionFilter extends OncePerRequestFilter {

    public static final String VERSION_HEADER = "X-App-Version";
    public static final String UPDATE_REQUIRED = "APP_UPDATE_REQUIRED";

    private final RaceTrackerProperties properties;
    private final ObjectMapper objectMapper;

    public MinimumAppVersionFilter(RaceTrackerProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        String minimum = properties.getMobile().getMinimumVersion();
        if (minimum != null && !minimum.isBlank() && AppVersions.parse(minimum).isEmpty()) {
            throw new IllegalStateException("racetracker.mobile.minimum-version must look like 1.4.0");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.endsWith("/public/app-config") || path.contains("/actuator/") || "OPTIONS".equals(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        String minimum = properties.getMobile().getMinimumVersion();
        String version = request.getHeader(VERSION_HEADER);
        if (minimum == null || minimum.isBlank() || version == null || !AppVersions.isBelow(version, minimum)) {
            chain.doFilter(request, response);
            return;
        }
        HttpStatus status = HttpStatus.UPGRADE_REQUIRED;
        response.setStatus(status.value());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(status)
            .statusCode(status.value())
            .error(status.getReasonPhrase())
            .message("This version of the app is no longer supported. Update the app to continue.")
            .code(UPDATE_REQUIRED)
            .requestId(MDC.get("requestId"))
            .path(request.getRequestURI())
            .build());
    }
}
