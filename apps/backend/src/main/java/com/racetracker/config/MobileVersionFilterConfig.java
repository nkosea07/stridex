package com.racetracker.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.racetracker.security.MinimumAppVersionFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
public class MobileVersionFilterConfig {

    @Bean
    public FilterRegistrationBean<MinimumAppVersionFilter> minimumAppVersionFilter(RaceTrackerProperties properties,
                                                                                 ObjectMapper objectMapper) {
        FilterRegistrationBean<MinimumAppVersionFilter> registration =
            new FilterRegistrationBean<>(new MinimumAppVersionFilter(properties, objectMapper));
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return registration;
    }
}
