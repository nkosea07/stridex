package com.racetracker.config;

import com.racetracker.security.CustomUserDetailsService;
import com.racetracker.security.JwtAuthenticationFilter;
import com.racetracker.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.mail.MailSenderAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
    classes = ActuatorSecurityIntegrationTest.TestApplication.class,
    properties = {
        "jwt.secret=test-jwt-secret-for-actuator-security",
        "racetracker.api.version=1",
        "cors.allowed-origins=http://localhost:5173",
        "server.servlet.context-path=/api"
    }
)
@AutoConfigureMockMvc
class ActuatorSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @Test
    void healthEndpoints_allowAnonymousAccess() throws Exception {
        mockMvc.perform(apiGet("/actuator/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(apiGet("/actuator/health/liveness"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").exists());

        mockMvc.perform(apiGet("/actuator/health/readiness"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").exists());
    }

    @Test
    void infoEndpoint_isAnonymousAndLimitedToSafeApplicationMetadata() throws Exception {
        mockMvc.perform(apiGet("/actuator/info"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.app.name").value("race-tracker-backend"))
            .andExpect(jsonPath("$.app.description").value("Backend service for Race/Marathon Tracking Platform"))
            .andExpect(jsonPath("$.env").doesNotExist())
            .andExpect(jsonPath("$.java").doesNotExist())
            .andExpect(jsonPath("$.os").doesNotExist())
            .andExpect(jsonPath("$.process").doesNotExist());
    }

    @Test
    void prometheusEndpoint_isNotPubliclyAccessible() throws Exception {
        mockMvc.perform(apiGet("/actuator/prometheus"))
            .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(401, 403));
    }

    private RequestBuilder apiGet(String path) {
        return get("/api" + path).contextPath("/api");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
        DataSourceAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        JpaRepositoriesAutoConfiguration.class,
        RedisAutoConfiguration.class,
        RedisRepositoriesAutoConfiguration.class,
        MailSenderAutoConfiguration.class
    })
    @EnableConfigurationProperties({CorsProperties.class, RaceTrackerProperties.class})
    @Import({SecurityConfig.class, JwtAuthenticationFilter.class})
    static class TestApplication {
    }
}
