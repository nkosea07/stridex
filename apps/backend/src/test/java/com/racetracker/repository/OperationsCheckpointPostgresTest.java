package com.racetracker.repository;

import com.racetracker.dto.checkpoint.OperationCheckpointDto;
import com.racetracker.model.User;
import com.racetracker.dto.emergency.EmergencyAlertRequest;
import com.racetracker.security.RaceAccessGuard;
import com.racetracker.service.AuditEventService;
import com.racetracker.service.CheckpointEmergencyService;
import com.racetracker.service.impl.EmergencyAlertServiceImpl;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.aop.support.AopUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({CheckpointEmergencyService.class, EmergencyAlertServiceImpl.class, RaceAccessGuard.class})
@EnabledIfEnvironmentVariable(named = "FLYWAY_TEST_URL", matches = ".+")
class OperationsCheckpointPostgresTest {
    @DynamicPropertySource
    static void postgres(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("FLYWAY_TEST_URL"));
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("FLYWAY_TEST_USERNAME", ""));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("FLYWAY_TEST_PASSWORD", ""));
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired CheckpointRepository checkpoints;
    @Autowired EntityManager entityManager;
    @Autowired CheckpointEmergencyService emergency;
    @Autowired PlatformTransactionManager transactionManager;
    @MockBean AuditEventService audit;

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listsDirectAndActiveRaceAssignmentsWithoutDuplicatesOrUnrelatedRaces() {
        String email = "operations-" + UUID.randomUUID() + "@example.test";
        UUID volunteer = user(email);
        UUID other = user("other-" + UUID.randomUUID() + "@example.test");
        UUID raceLevel = race("Race staff", false);
        UUID checkpointOnly = race("Direct", false);
        UUID inactive = race("Inactive", false);
        UUID revoked = race("Revoked", false);
        UUID archived = race("Archived", true);
        UUID unrelated = race("Unrelated", false);

        UUID first = checkpoint(raceLevel, "First", 1);
        UUID second = checkpoint(raceLevel, "Second", 2);
        UUID direct = checkpoint(checkpointOnly, "Direct", 1);
        checkpoint(inactive, "Inactive", 1);
        checkpoint(revoked, "Revoked", 1);
        UUID archivedCheckpoint = checkpoint(archived, "Archived", 1);
        UUID unrelatedCheckpoint = checkpoint(unrelated, "Unrelated", 1);

        assignRace(raceLevel, volunteer, true, false);
        assignRace(inactive, volunteer, false, true);
        assignRace(revoked, volunteer, false, true);
        assignRace(unrelated, other, true, false);
        assignCheckpoint(first, volunteer);
        assignCheckpoint(direct, volunteer);
        assignCheckpoint(archivedCheckpoint, volunteer);
        assignCheckpoint(unrelatedCheckpoint, other);

        Page<OperationCheckpointDto> all = checkpoints.findOperationsForVolunteer(email,
            User.UserRole.VOLUNTEER, PageRequest.of(0, 50));
        assertThat(all.getTotalElements()).isEqualTo(3);
        assertThat(all.getContent()).extracting(OperationCheckpointDto::checkpointId)
            .containsExactlyInAnyOrder(first, second, direct);
        assertThat(all.getContent()).extracting(OperationCheckpointDto::raceName)
            .containsExactlyInAnyOrder("Race staff", "Race staff", "Direct");

        List<OperationCheckpointDto> paged = new ArrayList<>();
        for (int page = 0; page < 3; page++) {
            Page<OperationCheckpointDto> slice = checkpoints.findOperationsForVolunteer(email,
                User.UserRole.VOLUNTEER, PageRequest.of(page, 1));
            assertThat(slice.getTotalElements()).isEqualTo(3);
            paged.addAll(slice.getContent());
        }
        assertThat(paged).containsExactlyElementsOf(all.getContent());
        assertThat(paged).isSortedAccordingTo(Comparator
            .comparing((OperationCheckpointDto item) -> item.raceId().toString())
            .thenComparing(item -> item.checkpointId().equals(first) ? 1 : item.checkpointId().equals(second) ? 2 : 1));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void failedAuditRollsBackAlertAndNotificationJob() {
        String email = "sos-" + UUID.randomUUID() + "@example.test";
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        assertThat(AopUtils.isAopProxy(emergency)).isTrue();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        UUID[] ids = transaction.execute(status -> {
            UUID volunteer = user(email);
            UUID raceId = race("Rollback SOS", false);
            jdbc.update("UPDATE races SET created_by = ? WHERE id = ?", volunteer, raceId);
            UUID checkpointId = checkpoint(raceId, "SOS point", 1);
            assignCheckpoint(checkpointId, volunteer);
            return new UUID[] {volunteer, raceId, checkpointId};
        });
        UUID volunteer = ids[0];
        UUID raceId = ids[1];
        UUID checkpointId = ids[2];
        AtomicInteger observedAlerts = new AtomicInteger();
        AtomicInteger observedJobs = new AtomicInteger();
        try {
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(email, null,
                List.of(new SimpleGrantedAuthority("ROLE_VOLUNTEER"))));
            doAnswer(invocation -> {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
                entityManager.flush();
                observedAlerts.set(alertCount(raceId));
                observedJobs.set(jobCount(raceId));
                throw new IllegalStateException("audit failed");
            }).when(audit).recordForActor(any(), any(), any(), any(), any(), any());

            assertThatThrownBy(() -> emergency.raise(raceId, checkpointId, new EmergencyAlertRequest(), email))
                .isInstanceOf(IllegalStateException.class);
            assertThat(observedAlerts.get()).isEqualTo(1);
            assertThat(observedJobs.get()).isGreaterThanOrEqualTo(1);
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();

            transaction.execute(status -> {
                assertThat(jdbc.queryForObject("SELECT count(*) FROM races WHERE id = ?", Integer.class, raceId))
                    .isEqualTo(1);
                assertThat(jdbc.queryForObject("SELECT count(*) FROM checkpoint_volunteers "
                    + "WHERE checkpoint_id = ? AND user_id = ?", Integer.class, checkpointId, volunteer))
                    .isEqualTo(1);
                assertThat(alertCount(raceId)).isZero();
                assertThat(jobCount(raceId)).isZero();
                return null;
            });
        } finally {
            transaction.execute(status -> {
                jdbc.update("DELETE FROM emergency_notification_jobs WHERE alert_id IN "
                    + "(SELECT id FROM emergency_alerts WHERE race_id = ?)", raceId);
                jdbc.update("DELETE FROM emergency_alerts WHERE race_id = ?", raceId);
                jdbc.update("DELETE FROM checkpoint_volunteers WHERE checkpoint_id = ?", checkpointId);
                jdbc.update("DELETE FROM checkpoints WHERE id = ?", checkpointId);
                jdbc.update("DELETE FROM races WHERE id = ?", raceId);
                jdbc.update("DELETE FROM users WHERE id = ?", volunteer);
                return null;
            });
        }
    }

    private int alertCount(UUID raceId) {
        return jdbc.queryForObject("SELECT count(*) FROM emergency_alerts WHERE race_id = ?", Integer.class, raceId);
    }

    private int jobCount(UUID raceId) {
        return jdbc.queryForObject("SELECT count(*) FROM emergency_notification_jobs j "
            + "JOIN emergency_alerts a ON a.id = j.alert_id WHERE a.race_id = ?", Integer.class, raceId);
    }

    private UUID user(String email) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO users (id, email, password_hash, first_name, last_name, role) "
            + "VALUES (?, ?, 'test', 'Test', 'Volunteer', 'VOLUNTEER'::user_role)", id, email);
        return id;
    }

    private UUID race(String name, boolean archived) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO races (id, name, race_type, race_date, archived_at) "
            + "VALUES (?, ?, 'MARATHON'::race_type, CURRENT_DATE, "
            + (archived ? "CURRENT_TIMESTAMP" : "NULL") + ")", id, name);
        return id;
    }

    private UUID checkpoint(UUID raceId, String name, int order) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO checkpoints (id, race_id, name, checkpoint_type, order_index) "
            + "VALUES (?, ?, ?, 'INTERMEDIATE'::checkpoint_type, ?)", id, raceId, name, order);
        return id;
    }

    private void assignRace(UUID raceId, UUID userId, boolean active, boolean revoked) {
        jdbc.update("INSERT INTO race_staff_assignments "
            + "(id, race_id, user_id, staff_role, active, revoked_at) "
            + "VALUES (?, ?, ?, 'VOLUNTEER'::user_role, ?, "
            + (revoked ? "CURRENT_TIMESTAMP" : "NULL") + ")", UUID.randomUUID(), raceId, userId, active);
    }

    private void assignCheckpoint(UUID checkpointId, UUID userId) {
        jdbc.update("INSERT INTO checkpoint_volunteers (checkpoint_id, user_id) VALUES (?, ?)", checkpointId, userId);
    }
}
