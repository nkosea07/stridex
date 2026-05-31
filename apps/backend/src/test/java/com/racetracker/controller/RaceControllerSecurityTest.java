package com.racetracker.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class RaceControllerSecurityTest {

    @Test
    void endpointRoleGuards_matchDomainRoles() {
        Map<String, String> guardsByMethod = Arrays.stream(RaceController.class.getDeclaredMethods())
            .filter(method -> method.isAnnotationPresent(PreAuthorize.class))
            .collect(Collectors.toMap(Method::getName, method -> method.getAnnotation(PreAuthorize.class).value()));

        assertThat(guardsByMethod.get("list"))
            .isEqualTo("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL', 'ATHLETE', 'SPECTATOR')");
        assertThat(guardsByMethod.get("get"))
            .isEqualTo("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL', 'ATHLETE', 'SPECTATOR')");
        assertThat(guardsByMethod.get("create"))
            .isEqualTo("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')");
        assertThat(guardsByMethod.get("update"))
            .isEqualTo("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')");
        assertThat(guardsByMethod.get("updateStatus"))
            .isEqualTo("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')");
        assertThat(guardsByMethod.get("delete"))
            .isEqualTo("hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')");
    }
}
