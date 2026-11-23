package com.racetracker.database;

import com.racetracker.repository.ParticipantRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the repository's result SQL against PostgreSQL: positions must follow each finisher's own elapsed
 * time (chip start, else wave gun, else race gun) plus penalties, and registrations that never had a place
 * in the race must not appear.
 */
@EnabledIfEnvironmentVariable(named = "FLYWAY_TEST_URL", matches = ".+")
class ResultRankingPostgresTest {

    private static final Pattern PARAMETER = Pattern.compile("(?<![:\\w]):(raceId|userId|publishedOnly|participantId)\\b");
    private static final Map<String, String> PARAMETER_TYPES = Map.of(
        "raceId", "uuid", "userId", "uuid", "participantId", "uuid", "publishedOnly", "boolean");

    private static String url;
    private static String username;
    private static String password;

    @BeforeAll
    static void migrate() throws Exception {
        url = System.getenv("FLYWAY_TEST_URL");
        username = System.getenv().getOrDefault("FLYWAY_TEST_USERNAME", "");
        password = System.getenv().getOrDefault("FLYWAY_TEST_PASSWORD", "");
        try (Connection connection = DriverManager.getConnection(url, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("DROP EXTENSION IF EXISTS postgis_tiger_geocoder CASCADE");
            statement.execute("DROP EXTENSION IF EXISTS postgis_topology CASCADE");
            statement.execute("DROP EXTENSION IF EXISTS fuzzystrmatch CASCADE");
            statement.execute("DROP EXTENSION IF EXISTS postgis CASCADE");
        }
        Flyway flyway = Flyway.configure()
            .dataSource(url, username, password)
            .locations("classpath:db/migration")
            .cleanDisabled(false)
            .load();
        flyway.clean();
        flyway.migrate();
    }

    @Test
    void ranksFinishersByElapsedTimeAndListsOnlyEntrants() throws Exception {
        UUID raceId = UUID.randomUUID();
        UUID waveOne = UUID.randomUUID();
        UUID waveTwo = UUID.randomUUID();
        UUID start = UUID.randomUUID();
        UUID finish = UUID.randomUUID();
        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            execute(connection, "INSERT INTO races (id, name, race_type, race_date, start_time, timezone, status, started_at) "
                + "VALUES (?, 'Wave Test', 'MARATHON', DATE '2026-11-22', TIME '08:00', 'UTC', 'COMPLETED', "
                + "TIMESTAMPTZ '2026-11-22 08:00:00+00')", raceId);
            execute(connection, "INSERT INTO race_waves (id, race_id, name, started_at) VALUES "
                + "(?, ?, 'Wave 1', TIMESTAMPTZ '2026-11-22 08:00:00+00')", waveOne, raceId);
            execute(connection, "INSERT INTO race_waves (id, race_id, name, started_at) VALUES "
                + "(?, ?, 'Wave 2', TIMESTAMPTZ '2026-11-22 08:10:00+00')", waveTwo, raceId);
            execute(connection, "INSERT INTO checkpoints (id, race_id, name, checkpoint_type, order_index) "
                + "VALUES (?, ?, 'Start', 'START', 0)", start, raceId);
            execute(connection, "INSERT INTO checkpoints (id, race_id, name, checkpoint_type, order_index) "
                + "VALUES (?, ?, 'Finish', 'FINISH', 1)", finish, raceId);

            UUID earlyClockFinisher = participant(connection, raceId, waveOne, "FINISHED", "A");
            UUID laterWaveFastest = participant(connection, raceId, waveTwo, "FINISHED", "B");
            UUID chipStarter = participant(connection, raceId, waveOne, "FINISHED", "C");
            UUID penalised = participant(connection, raceId, waveOne, "FINISHED", "D");
            UUID noShow = participant(connection, raceId, waveOne, "DNS", "E");
            UUID rejected = participant(connection, raceId, null, "REJECTED", "F");
            UUID waitlisted = participant(connection, raceId, null, "WAITLISTED", "G");
            UUID pending = participant(connection, raceId, null, "PENDING_REVIEW", "H");
            UUID withdrawn = participant(connection, raceId, waveOne, "WITHDRAWN", "I");

            record(connection, raceId, earlyClockFinisher, finish, "2026-11-22 09:05:00+00");
            record(connection, raceId, laterWaveFastest, finish, "2026-11-22 09:12:00+00");
            record(connection, raceId, chipStarter, start, "2026-11-22 08:03:00+00");
            record(connection, raceId, chipStarter, finish, "2026-11-22 09:06:00+00");
            record(connection, raceId, penalised, finish, "2026-11-22 09:01:00+00");
            execute(connection, "INSERT INTO participant_penalties (race_id, participant_id, seconds) VALUES (?, ?, 300)",
                raceId, penalised);

            Map<UUID, Long> positions = new LinkedHashMap<>();
            try (PreparedStatement rows = prepare(connection, "findResultRows", raceId)) {
                try (ResultSet resultSet = rows.executeQuery()) {
                    while (resultSet.next()) {
                        positions.put(resultSet.getObject("participantId", UUID.class),
                            (Long) resultSet.getObject("overallPosition"));
                    }
                }
            }

            assertThat(positions.keySet())
                .containsExactly(laterWaveFastest, chipStarter, earlyClockFinisher, penalised, noShow)
                .doesNotContain(rejected, waitlisted, pending, withdrawn);
            assertThat(positions.get(laterWaveFastest)).isEqualTo(1L);
            assertThat(positions.get(chipStarter)).isEqualTo(2L);
            assertThat(positions.get(earlyClockFinisher)).isEqualTo(3L);
            assertThat(positions.get(penalised)).isEqualTo(4L);
            assertThat(positions.get(noShow)).isNull();

            try (PreparedStatement count = prepare(connection, "countResultRows", raceId);
                 ResultSet resultSet = count.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getLong(1)).isEqualTo(5L);
            }
        }
    }

    private static UUID participant(Connection connection, UUID raceId, UUID waveId, String status, String name) throws Exception {
        UUID id = UUID.randomUUID();
        execute(connection, "INSERT INTO participants (id, race_id, wave_id, first_name, last_name, email, athlete_code, status) "
                + "VALUES (?, ?, ?, ?, 'Runner', ?, ?, CAST(? AS participant_status))",
            id, raceId, waveId, name, name.toLowerCase() + "-" + id + "@example.test", "ATH-" + id.toString().substring(0, 12),
            status);
        return id;
    }

    private static void record(Connection connection, UUID raceId, UUID participantId, UUID checkpointId, String at)
        throws Exception {
        execute(connection, "INSERT INTO checkpoint_records (race_id, participant_id, checkpoint_id, recorded_at, record_method) "
            + "VALUES (?, ?, ?, CAST(? AS timestamptz), 'RFID')", raceId, participantId, checkpointId, at);
    }

    private static void execute(Connection connection, String sql, Object... values) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) {
                if (values[i] == null) {
                    statement.setNull(i + 1, Types.OTHER);
                } else {
                    statement.setObject(i + 1, values[i]);
                }
            }
            statement.executeUpdate();
        }
    }

    private static PreparedStatement prepare(Connection connection, String method, UUID raceId) throws Exception {
        String sql = switch (method) {
            case "findResultRows" -> ParticipantRepository.class.getMethod(method, UUID.class, UUID.class, boolean.class,
                UUID.class, Pageable.class).getAnnotation(Query.class).value();
            case "countResultRows" -> ParticipantRepository.class.getMethod(method, UUID.class, UUID.class, boolean.class)
                .getAnnotation(Query.class).value();
            default -> throw new IllegalArgumentException(method);
        };
        Map<String, Object> values = Map.of("raceId", raceId, "publishedOnly", false);
        List<Object> bound = new ArrayList<>();
        Matcher matcher = PARAMETER.matcher(sql);
        StringBuilder jdbc = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            bound.add(values.get(name));
            matcher.appendReplacement(jdbc, "CAST(? AS " + PARAMETER_TYPES.get(name) + ")");
        }
        matcher.appendTail(jdbc);
        PreparedStatement statement = connection.prepareStatement(jdbc.toString());
        for (int i = 0; i < bound.size(); i++) {
            if (bound.get(i) == null) {
                statement.setNull(i + 1, Types.OTHER);
            } else {
                statement.setObject(i + 1, bound.get(i));
            }
        }
        return statement;
    }
}
