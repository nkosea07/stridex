package com.racetracker.controller;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class ControllerAuthorizationMatrixTest {

    @Test
    void authControllerGuards() {
        assertGuard(AuthController.class, "me", "isAuthenticated()");
        assertGuard(AuthController.class, "updateProfile", "isAuthenticated()");
    }

    @Test
    void participantControllerGuards() {
        assertGuard(ParticipantController.class, "myRegistrations", "isAuthenticated()");
    }

    @Test
    void raceParticipantControllerGuards() {
        String opsGuard = "hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL')";
        assertGuard(RaceParticipantController.class, "list", opsGuard);
        assertGuard(RaceParticipantController.class, "get", opsGuard);
        assertGuard(RaceParticipantController.class, "create", opsGuard);
        assertGuard(RaceParticipantController.class, "update", opsGuard);
        assertGuard(RaceParticipantController.class, "delete", opsGuard);
        assertGuard(RaceParticipantController.class, "checkIn", opsGuard);
        assertGuard(RaceParticipantController.class, "importCsv", opsGuard);
        assertGuard(RaceParticipantController.class, "exportCsv", opsGuard);
        assertGuard(RaceParticipantController.class, "registerForRace", "isAuthenticated()");
    }

    @Test
    void checkpointControllerGuards() {
        assertGuard(CheckpointController.class, "list", "hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL', 'ATHLETE', 'SPECTATOR')");
        String opsGuard = "hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL')";
        assertGuard(CheckpointController.class, "create", opsGuard);
        assertGuard(CheckpointController.class, "update", opsGuard);
        assertGuard(CheckpointController.class, "delete", opsGuard);
        assertGuard(CheckpointController.class, "record", opsGuard);
    }

    @Test
    void rfidControllerGuards() {
        String opsGuard = "hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'OFFICIAL')";
        assertGuard(RfidController.class, "createReader", opsGuard);
        assertGuard(RfidController.class, "listReaders", opsGuard);
        assertGuard(RfidController.class, "revokeReader", opsGuard);
        assertThat(Arrays.stream(RfidController.class.getDeclaredMethods())
            .filter(method -> method.getName().equals("ingestReads"))
            .noneMatch(method -> method.isAnnotationPresent(PreAuthorize.class)))
            .isTrue();
    }

    @Test
    void trackingControllerGuards() {
        String trackOpsGuard = "hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL', 'ATHLETE')";
        assertGuard(TrackingController.class, "sendLocation", trackOpsGuard);
        assertGuard(TrackingController.class, "sendLocationsBatch", trackOpsGuard);
        assertGuard(TrackingController.class, "recordCheckpoint", trackOpsGuard);
        String liveGuard = "hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL', 'ATHLETE', 'SPECTATOR')";
        assertGuard(TrackingController.class, "getLiveData", liveGuard);
        assertGuard(TrackingController.class, "getParticipantTrack", liveGuard);
    }

    @Test
    void resultControllerGuards() {
        String readGuard = "hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL', 'ATHLETE', 'SPECTATOR')";
        assertGuard(ResultController.class, "listByRace", readGuard);
        assertGuard(ResultController.class, "getByParticipant", readGuard);
        assertGuard(ResultController.class, "myResults", "isAuthenticated()");
        assertGuard(ResultController.class, "publish", "hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL')");
    }

    @Test
    void dashboardControllerGuards() {
        assertGuard(DashboardController.class, "stats", "hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')");
    }

    @Test
    void publicTrackingControllerHasNoMethodGuards() {
        assertThat(Arrays.stream(PublicTrackingController.class.getDeclaredMethods())
            .noneMatch(method -> method.isAnnotationPresent(PreAuthorize.class)))
            .isTrue();
    }

    @Test
    void notificationControllerGuards() {
        assertGuard(NotificationController.class, "list", "isAuthenticated()");
        assertGuard(NotificationController.class, "markRead", "isAuthenticated()");
    }

    @Test
    void registrationControllerGuards() {
        assertGuard(RegistrationController.class, "cancelRegistration", "isAuthenticated()");
    }

    private void assertGuard(Class<?> controllerClass, String methodName, String expected) {
        Map<String, String> guardsByMethod = Arrays.stream(controllerClass.getDeclaredMethods())
            .filter(method -> method.isAnnotationPresent(PreAuthorize.class))
            .collect(Collectors.toMap(Method::getName, method -> method.getAnnotation(PreAuthorize.class).value()));

        assertThat(guardsByMethod.get(methodName)).isEqualTo(expected);
    }
}
