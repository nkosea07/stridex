package com.racetracker.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.racetracker.config.RaceTrackerProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MinimumAppVersionFilterTest {

    private MinimumAppVersionFilter filter(String minimum) {
        RaceTrackerProperties properties = new RaceTrackerProperties();
        properties.getMobile().setMinimumVersion(minimum);
        return new MinimumAppVersionFilter(properties, new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    private MockHttpServletResponse run(MinimumAppVersionFilter filter, String path, String version) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        if (version != null) {
            request.addHeader(MinimumAppVersionFilter.VERSION_HEADER, version);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void outdatedAppsGetUpgradeRequiredWithAStableCode() throws Exception {
        MockHttpServletResponse response = run(filter("1.4.0"), "/api/v1/races", "1.3.9");

        assertThat(response.getStatus()).isEqualTo(426);
        assertThat(response.getContentAsString()).contains("\"code\":\"APP_UPDATE_REQUIRED\"");
    }

    @Test
    void currentAppsBrowsersAndTheConfigEndpointPassThrough() throws Exception {
        MinimumAppVersionFilter filter = filter("1.4.0");

        assertThat(run(filter, "/api/v1/races", "1.4.0").getStatus()).isEqualTo(200);
        assertThat(run(filter, "/api/v1/races", null).getStatus()).isEqualTo(200);
        assertThat(run(filter, "/api/v1/public/app-config", "1.0.0").getStatus()).isEqualTo(200);
        assertThat(run(filter(""), "/api/v1/races", "0.0.1").getStatus()).isEqualTo(200);
    }

    @Test
    void aMalformedMinimumFailsAtStartup() {
        assertThatThrownBy(() -> filter("latest")).isInstanceOf(IllegalStateException.class);
    }
}
