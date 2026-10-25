package com.racetracker.config;

import com.racetracker.security.JwtAuthenticationFilter;
import com.racetracker.security.ApiSecurityExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CorsProperties corsProperties;
    private final RaceTrackerProperties raceTrackerProperties;
    private final ApiSecurityExceptionHandler apiSecurityExceptionHandler;

    public SecurityConfig(
        JwtAuthenticationFilter jwtAuthenticationFilter,
        CorsProperties corsProperties,
        RaceTrackerProperties raceTrackerProperties,
        ApiSecurityExceptionHandler apiSecurityExceptionHandler
    ) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.corsProperties = corsProperties;
        this.raceTrackerProperties = raceTrackerProperties;
        this.apiSecurityExceptionHandler = apiSecurityExceptionHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        String apiPrefix = "/v" + raceTrackerProperties.getApi().getVersion();

        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exceptionHandling -> exceptionHandling
                .authenticationEntryPoint(apiSecurityExceptionHandler)
                .accessDeniedHandler(apiSecurityExceptionHandler))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                .requestMatchers("/actuator/prometheus").authenticated()
                .requestMatchers(
                    apiPrefix + "/auth/login",
                    apiPrefix + "/auth/deletion/cancel",
                    apiPrefix + "/auth/browser/login",
                    apiPrefix + "/auth/browser/mfa/totp/enroll",
                    apiPrefix + "/auth/browser/mfa/totp/confirm",
                    apiPrefix + "/auth/browser/mfa/verify-login",
                    apiPrefix + "/auth/browser/refresh",
                    apiPrefix + "/auth/browser/logout",
                    apiPrefix + "/auth/mfa/totp/enroll",
                    apiPrefix + "/auth/mfa/totp/confirm",
                    apiPrefix + "/auth/mfa/verify-login",
                    apiPrefix + "/auth/register",
                    apiPrefix + "/auth/register-organizer",
                    apiPrefix + "/auth/oauth/google",
                    apiPrefix + "/auth/refresh",
                    apiPrefix + "/auth/forgot-password",
                    apiPrefix + "/auth/reset-password",
                    apiPrefix + "/auth/verify-email",
                    apiPrefix + "/auth/resend-verification"
                ).permitAll()
                .requestMatchers(HttpMethod.POST, apiPrefix + "/rfid/read-events").permitAll()
                .requestMatchers(HttpMethod.POST, apiPrefix + "/rfid/readers/heartbeat").permitAll()
                .requestMatchers(HttpMethod.POST, apiPrefix + "/payments/callback/**").permitAll()
                .requestMatchers(HttpMethod.GET, apiPrefix + "/public/races/**").permitAll()
                .requestMatchers("/ws-tracking", "/ws-tracking/**").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                .requestMatchers("/error").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(corsProperties.getAllowedOrigins());
        config.setAllowedMethods(emptyFallback(corsProperties.getAllowedMethods(),
            List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")));
        config.setAllowedHeaders(corsProperties.getAllowedHeaders());
        config.setAllowCredentials(corsProperties.isAllowCredentials());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public InfoContributor appInfoContributor(
        @Value("${spring.application.name}") String applicationName,
        @Value("${info.app.description:}") String applicationDescription
    ) {
        return (Info.Builder builder) -> builder.withDetail("app", java.util.Map.of(
            "name", applicationName,
            "description", applicationDescription
        ));
    }

    private List<String> emptyFallback(List<String> input, List<String> fallback) {
        return (input == null || input.isEmpty()) ? fallback : input;
    }
}
