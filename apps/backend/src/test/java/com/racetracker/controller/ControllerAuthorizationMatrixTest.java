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
        assertGuard(AuthController.class, "updateCapabilities", "isAuthenticated()");
    }

    @Test
    void participantControllerGuards() {
        assertGuard(ParticipantController.class, "myRegistrations", "hasAuthority('CAP_ATHLETE')");
    }

    @Test
    void raceParticipantControllerGuards() {
        String opsGuard = "hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL')";
        String adminGuard = "hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')";
        assertGuard(RaceParticipantController.class, "list", opsGuard);
        assertGuard(RaceParticipantController.class, "get", opsGuard);
        assertGuard(RaceParticipantController.class, "create", adminGuard);
        assertGuard(RaceParticipantController.class, "update", adminGuard);
        assertGuard(RaceParticipantController.class, "delete", adminGuard);
        assertGuard(RaceParticipantController.class, "checkIn", opsGuard);
        assertGuard(RaceParticipantController.class, "importCsv", adminGuard);
        assertGuard(RaceParticipantController.class, "exportCsv", adminGuard);
        assertGuard(RaceParticipantController.class, "registerForRace", "hasAuthority('CAP_ATHLETE')");
        assertGuard(RaceParticipantController.class, "review", "hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')");
        assertGuard(RaceParticipantController.class, "verifyPayment", "hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')");
    }

    @Test
    void raceRegistrationFieldControllerGuards() {
        String adminGuard = "hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')";
        assertGuard(RaceRegistrationFieldController.class, "list", adminGuard);
        assertGuard(RaceRegistrationFieldController.class, "create", adminGuard);
        assertGuard(RaceRegistrationFieldController.class, "update", adminGuard);
        assertGuard(RaceRegistrationFieldController.class, "delete", adminGuard);
    }

    @Test
    void raceTeamControllerGuards() {
        assertGuard(RaceTeamController.class, "list", "hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL')");
        assertGuard(RaceTeamController.class, "create", "hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')");
    }

    @Test
    void favoriteControllerGuards() {
        assertGuard(FavoriteController.class, "list", "hasAuthority('CAP_SPECTATOR')");
        assertGuard(FavoriteController.class, "follow", "hasAuthority('CAP_SPECTATOR')");
        assertGuard(FavoriteController.class, "unfollow", "hasAuthority('CAP_SPECTATOR')");
    }

    @Test
    void checkpointControllerGuards() {
        assertGuard(CheckpointController.class, "list", "hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL', 'ATHLETE', 'SPECTATOR')");
        String opsGuard = "hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL')";
        String adminGuard = "hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')";
        assertGuard(CheckpointController.class, "create", adminGuard);
        assertGuard(CheckpointController.class, "update", adminGuard);
        assertGuard(CheckpointController.class, "delete", adminGuard);
        assertGuard(CheckpointController.class, "record", opsGuard);
        assertGuard(CheckpointController.class, "export", opsGuard);
        assertGuard(CheckpointController.class, "listRecords", opsGuard);
        assertGuard(CheckpointController.class, "correctRecord", "hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')");
    }

    @Test
    void raceCategoryControllerGuards() {
        assertGuard(RaceCategoryController.class, "list", "hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL')");
        assertGuard(RaceCategoryController.class, "create", "hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')");
        assertGuard(RaceCategoryController.class, "update", "hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')");
        assertGuard(RaceCategoryController.class, "delete", "hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')");
    }

    @Test
    void rfidControllerGuards() {
        String opsGuard = "hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'OFFICIAL')";
        String adminGuard = "hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')";
        assertGuard(RfidController.class, "createReader", adminGuard);
        assertGuard(RfidController.class, "listReaders", opsGuard);
        assertGuard(RfidController.class, "overview", opsGuard);
        assertGuard(RfidController.class, "revokeReader", adminGuard);
        assertThat(Arrays.stream(RfidController.class.getDeclaredMethods())
            .filter(method -> method.getName().equals("ingestReads"))
            .noneMatch(method -> method.isAnnotationPresent(PreAuthorize.class)))
            .isTrue();
    }

    @Test
    void trackingControllerGuards() {
        String trackOpsGuard = "hasAuthority('CAP_ATHLETE')";
        assertGuard(TrackingController.class, "sendLocation", trackOpsGuard);
        assertGuard(TrackingController.class, "sendLocationsBatch", trackOpsGuard);
        assertGuard(TrackingController.class, "recordCheckpoint", trackOpsGuard);
        String liveGuard = "hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL')";
        assertGuard(TrackingController.class, "getLiveData", liveGuard);
        assertGuard(TrackingController.class, "getParticipantTrack", liveGuard);
    }

    @Test
    void resultControllerGuards() {
        String readGuard = "hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL')";
        assertGuard(ResultController.class, "listByRace", readGuard);
        assertGuard(ResultController.class, "getByParticipant", readGuard);
        assertGuard(ResultController.class, "myResults", "hasAuthority('CAP_ATHLETE')");
        assertGuard(ResultController.class, "myResultsPage", "hasAuthority('CAP_ATHLETE')");
        assertGuard(ResultController.class, "publish", "hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')");
        assertThat(Arrays.stream(ResultController.class.getDeclaredMethods())
            .filter(method -> method.getName().equals("listPublishedPublicResults"))
            .noneMatch(method -> method.isAnnotationPresent(PreAuthorize.class)))
            .isTrue();
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
        assertGuard(NotificationController.class, "registerDevice", "isAuthenticated()");
        assertGuard(NotificationController.class, "unregisterDevice", "isAuthenticated()");
        assertGuard(NotificationController.class, "getPreferences", "isAuthenticated()");
        assertGuard(NotificationController.class, "updatePreferences", "isAuthenticated()");
    }

    @Test
    void accountPrivacyControllerGuards() {
        assertGuard(AccountPrivacyController.class, "exportData", "isAuthenticated()");
        assertGuard(AccountPrivacyController.class, "getConsent", "isAuthenticated()");
        assertGuard(AccountPrivacyController.class, "updateConsent", "isAuthenticated()");
        assertGuard(AccountPrivacyController.class, "requestDeletion", "isAuthenticated()");
        assertGuard(AccountPrivacyController.class, "cancelDeletion", "isAuthenticated()");
        assertGuard(AccountPrivacyController.class, "deletionStatus", "isAuthenticated()");
    }

    @Test
    void notificationTemplateControllerGuards() {
        assertGuard(NotificationTemplateController.class, "list", "hasRole('SUPER_ADMIN')");
        assertGuard(NotificationTemplateController.class, "create", "hasRole('SUPER_ADMIN')");
        assertGuard(NotificationTemplateController.class, "update", "hasRole('SUPER_ADMIN')");
        assertGuard(NotificationTemplateController.class, "delete", "hasRole('SUPER_ADMIN')");
    }

    @Test
    void raceInsightsControllerGuards() {
        String guard = "hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'OFFICIAL')";
        assertGuard(RaceInsightsController.class, "summary", guard);
        assertGuard(RaceInsightsController.class, "anomalies", guard);
    }

    @Test
    void emergencyAlertControllerGuards() {
        assertGuard(EmergencyAlertController.class, "raise",
            "hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN') or hasAuthority('CAP_ATHLETE')");
        assertGuard(EmergencyAlertController.class, "list", "hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'OFFICIAL')");
    }

    @Test
    void externalRefundReconciliationIsSuperAdminOnly() {
        assertGuard(PaymentController.class, "stageExternalRefund", "hasRole('SUPER_ADMIN')");
        assertGuard(PaymentController.class, "reconcileExternalRefund", "hasRole('SUPER_ADMIN')");
    }

    @Test
    void raceRouteControllerGuards() {
        assertGuard(RaceRouteController.class, "importGpx", "hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')");
        assertGuard(RaceRouteController.class, "route", "hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN', 'VOLUNTEER', 'OFFICIAL', 'ATHLETE')");
        assertThat(Arrays.stream(RaceRouteController.class.getDeclaredMethods())
            .filter(method -> method.getName().equals("publicRoute"))
            .noneMatch(method -> method.isAnnotationPresent(PreAuthorize.class)))
            .isTrue();
    }

    @Test
    void oauthControllerIsPublic() {
        assertThat(Arrays.stream(OAuthController.class.getDeclaredMethods())
            .noneMatch(method -> method.isAnnotationPresent(PreAuthorize.class)))
            .isTrue();
    }

    @Test
    void registrationControllerGuards() {
        assertGuard(RegistrationController.class, "cancelRegistration", "hasAuthority('CAP_ATHLETE')");
    }

    @Test
    void raceStaffAssignmentControllerGuards() {
        assertGuard(RaceStaffAssignmentController.class, "promote", "hasRole('SUPER_ADMIN')");
        String raceAdminGuard = "hasAnyRole('SUPER_ADMIN', 'RACE_ADMIN')";
        assertGuard(RaceStaffAssignmentController.class, "assign", raceAdminGuard);
        assertGuard(RaceStaffAssignmentController.class, "revoke", raceAdminGuard);
    }

    private void assertGuard(Class<?> controllerClass, String methodName, String expected) {
        Map<String, String> guardsByMethod = Arrays.stream(controllerClass.getDeclaredMethods())
            .filter(method -> method.isAnnotationPresent(PreAuthorize.class))
            .collect(Collectors.toMap(Method::getName, method -> method.getAnnotation(PreAuthorize.class).value()));

        assertThat(guardsByMethod.get(methodName)).isEqualTo(expected);
    }
}
