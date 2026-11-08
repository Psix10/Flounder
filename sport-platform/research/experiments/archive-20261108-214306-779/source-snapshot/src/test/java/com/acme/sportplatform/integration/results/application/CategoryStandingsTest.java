package com.acme.sportplatform.results.application;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.competition.EventDisciplineLookup;
import com.acme.sportplatform.competition.EventDisciplineSummary;
import com.acme.sportplatform.results.api.PublicDisciplineResultsResponse;
import com.acme.sportplatform.results.api.PublicDisciplineStandingsResponse;
import com.acme.sportplatform.results.infrastructure.jdbc.SqlDisciplineStandingsReader;
import com.fasterxml.jackson.databind.ObjectMapper;

@Testcontainers
@Execution(ExecutionMode.SAME_THREAD)
class CategoryStandingsTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16")
                    .withDatabaseName("category_standings_test")
                    .withUsername("test")
                    .withPassword("test");

    private final EventDisciplineLookup lookup =
            mock(EventDisciplineLookup.class);

    private final UUID event = UUID.randomUUID();
    private final UUID discipline = UUID.randomUUID();

    private Connection connection;
    private JdbcTemplate jdbc;
    private ObjectMapper json;
    private GetPublicDisciplineStandingsUseCase useCase;

    @BeforeEach
    void setup() throws Exception {
        connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword()
        );

        connection.setAutoCommit(false);

        jdbc = new JdbcTemplate(
                new SingleConnectionDataSource(connection, true)
        );

        json = new ObjectMapper();

        createTestTables();
        installExactParser();

        SqlDisciplineStandingsReader reader =
                new SqlDisciplineStandingsReader(jdbc, json);

        useCase = new GetPublicDisciplineStandingsUseCase(
                lookup,
                reader
        );

        policy("TIME", "ASC");
    }

    @AfterEach
    void cleanup() throws Exception {
        if (connection != null && !connection.isClosed()) {
            try {
                connection.rollback();
            } finally {
                connection.close();
            }
        }
    }

    private void createTestTables() {
        jdbc.execute("""
                CREATE TEMP TABLE event_disciplines (
                    id uuid PRIMARY KEY,
                    event_id uuid NOT NULL
                )
                """);

        jdbc.execute("""
                CREATE TEMP TABLE competition_units (
                    id uuid PRIMARY KEY,
                    event_discipline_id uuid NOT NULL,
                    label text NOT NULL,
                    sequence_number integer,
                    status text NOT NULL
                )
                """);

        jdbc.execute("""
                CREATE TEMP TABLE registrations (
                    id uuid PRIMARY KEY,
                    participant_snapshot jsonb NOT NULL
                )
                """);

        jdbc.execute("""
                CREATE TEMP TABLE competition_unit_entries (
                    id uuid PRIMARY KEY,
                    competition_unit_id uuid NOT NULL,
                    registration_id uuid NOT NULL,
                    category_code text,
                    seeding_group_code text
                )
                """);

        jdbc.execute("""
                CREATE TEMP TABLE results (
                    id uuid PRIMARY KEY,
                    competition_unit_entry_id uuid NOT NULL,
                    final_place integer,
                    raw_value text,
                    result_type text,
                    status text
                )
                """);
    }

    private void installExactParser() throws Exception {
        Path directory = Path.of(
                "src",
                "main",
                "resources",
                "db",
                "migration"
        );

        List<Path> migrations;

        try (var paths = Files.list(directory)) {
            migrations = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString()
                            .endsWith(
                                    "__add_exact_standings_numeric_parser.sql"
                            ))
                    .toList();
        }

        assertEquals(
                1,
                migrations.size(),
                "Ожидается одна новая миграция "
                        + "__add_exact_standings_numeric_parser.sql"
        );

        jdbc.execute(Files.readString(
                migrations.get(0),
                StandardCharsets.UTF_8
        ));
    }

    private void policy(String type, String order) {
        when(lookup.findById(discipline)).thenReturn(
                Optional.of(new EventDisciplineSummary(
                        discipline,
                        event,
                        "test",
                        "Test",
                        "INDIVIDUAL",
                        "HEAT",
                        type,
                        order,
                        40,
                        BigDecimal.ZERO,
                        "RUB",
                        "{}"
                ))
        );
    }

    private PublicDisciplineResultsResponse.EntryView entry(
            String name,
            String value,
            String category,
            String group
    ) {
        return new PublicDisciplineResultsResponse.EntryView(
                7,
                name,
                "Club",
                value,
                "TIME",
                "VALID",
                category,
                group
        );
    }

    private PublicDisciplineResultsResponse.UnitView heat(
            int sequence,
            PublicDisciplineResultsResponse.EntryView... entries
    ) {
        return new PublicDisciplineResultsResponse.UnitView(
                new UUID(0, sequence),
                "Heat " + sequence,
                sequence,
                List.of(entries)
        );
    }

    private PublicDisciplineStandingsResponse run(
            PublicDisciplineResultsResponse.UnitView... units
    ) throws Exception {
        jdbc.execute("""
                TRUNCATE TABLE
                    pg_temp.results,
                    pg_temp.competition_unit_entries,
                    pg_temp.registrations,
                    pg_temp.competition_units,
                    pg_temp.event_disciplines
                """);

        jdbc.update(
                "INSERT INTO event_disciplines (id, event_id) VALUES (?, ?)",
                discipline,
                event
        );

        for (var unit : units) {
            jdbc.update(
                    """
                    INSERT INTO competition_units (
                        id, event_discipline_id, label,
                        sequence_number, status
                    )
                    VALUES (?, ?, ?, ?, 'PUBLISHED')
                    """,
                    unit.id(),
                    discipline,
                    unit.label(),
                    unit.sequenceNumber()
            );

            for (var entry : unit.entries()) {
                UUID registrationId = UUID.randomUUID();
                UUID entryId = UUID.randomUUID();

                var snapshot = json.createObjectNode();
                snapshot.put("firstName", entry.participantName());
                snapshot.put("clubName", entry.clubName());

                jdbc.update(
                        """
                        INSERT INTO registrations (
                            id, participant_snapshot
                        )
                        VALUES (?, CAST(? AS jsonb))
                        """,
                        registrationId,
                        json.writeValueAsString(snapshot)
                );

                jdbc.update(
                        """
                        INSERT INTO competition_unit_entries (
                            id, competition_unit_id, registration_id,
                            category_code, seeding_group_code
                        )
                        VALUES (?, ?, ?, ?, ?)
                        """,
                        entryId,
                        unit.id(),
                        registrationId,
                        entry.categoryCode(),
                        entry.seedingGroupCode()
                );

                jdbc.update(
                        """
                        INSERT INTO results (
                            id, competition_unit_entry_id,
                            final_place, raw_value, result_type, status
                        )
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                        UUID.randomUUID(),
                        entryId,
                        entry.place(),
                        entry.rawValue(),
                        entry.resultType(),
                        entry.status()
                );
            }
        }

        return useCase.execute(event, discipline);
    }

    private List<Integer> categoryPlaces(
            PublicDisciplineStandingsResponse response
    ) {
        return response.entries().stream()
                .map(PublicDisciplineStandingsResponse.EntryView::categoryPlace)
                .toList();
    }

    private List<Integer> overallPlaces(
            PublicDisciplineStandingsResponse response
    ) {
        return response.entries().stream()
                .map(PublicDisciplineStandingsResponse.EntryView::overallPlace)
                .toList();
    }

    @Test
    void sameCategoryCombinesDifferentHeats() throws Exception {
        var response = run(
                heat(1, entry("Slow", "30", "A", "G1")),
                heat(2, entry("Fast", "28", "A", "G2"))
        );

        assertEquals(List.of(1, 2), categoryPlaces(response));
        assertEquals("Fast", response.entries().get(0).participantName());
    }

    @Test
    void separateCategoriesInSharedHeatStartAtOne() throws Exception {
        var response = run(
                heat(
                        1,
                        entry("A", "30", "A", "OPEN"),
                        entry("B", "28", "B", "OPEN")
                )
        );

        assertEquals(List.of(1, 1), categoryPlaces(response));
        assertEquals(List.of(1, 2), overallPlaces(response));
    }

    @Test
    void categoryIsNotSeedingGroup() throws Exception {
        var response = run(
                heat(
                        1,
                        entry("A", "30", "A", "OPEN"),
                        entry("B", "28", "B", "OPEN"),
                        entry("C", "29", "A", "OPEN")
                )
        );

        assertEquals(List.of(1, 1, 2), categoryPlaces(response));
    }

    @Test
    void tiesUseCompetitionPlacesWithinCategory() throws Exception {
        var response = run(
                heat(
                        1,
                        entry("A", "1:08.54", "A", "G"),
                        entry("B", "68.540", "A", "G"),
                        entry("C", "70", "A", "G")
                )
        );

        assertEquals(List.of(1, 1, 3), categoryPlaces(response));
        assertEquals(List.of(1, 1, 3), overallPlaces(response));
        assertEquals("COMPETITION", response.tiePolicy());
    }

    @Test
    void anotherCategoryDoesNotCreateAGap() throws Exception {
        var response = run(
                heat(
                        1,
                        entry("A", "10", "A", "G"),
                        entry("B", "10", "B", "G"),
                        entry("C", "11", "A", "G")
                )
        );

        assertEquals(List.of(1, 1, 2), categoryPlaces(response));
        assertEquals(List.of(1, 1, 3), overallPlaces(response));
    }

    @Test
    void descendingRankingIsIndependentPerCategory() throws Exception {
        policy("POINTS", "DESC");

        var a = new PublicDisciplineResultsResponse.EntryView(
                2, "A", "Club", "10", "POINTS", "VALID", "A", "G"
        );

        var b = new PublicDisciplineResultsResponse.EntryView(
                1, "B", "Club", "15", "POINTS", "VALID", "A", "G"
        );

        var c = new PublicDisciplineResultsResponse.EntryView(
                3, "C", "Club", "8", "POINTS", "VALID", "B", "G"
        );

        var response = run(heat(1, a, b, c));

        assertEquals(List.of(1, 2, 1), categoryPlaces(response));
        assertEquals(List.of(1, 2, 3), overallPlaces(response));
        assertEquals("B", response.entries().get(0).participantName());
    }

    @Test
    void specialStatusesKeepCategoryWithoutPlaces() throws Exception {
        for (String status : List.of(
                "DID_NOT_START",
                "DID_NOT_FINISH",
                "DISQUALIFIED"
        )) {
            var special = new PublicDisciplineResultsResponse.EntryView(
                    null,
                    "Special",
                    "Club",
                    null,
                    "TIME",
                    status,
                    "A",
                    "G"
            );

            var response = run(
                    heat(1, entry("Finisher", "30", "A", "G"), special)
            );

            assertEquals(
                    Integer.valueOf(1),
                    response.entries().get(0).categoryPlace()
            );

            var last = response.entries().get(1);

            assertNull(last.categoryPlace());
            assertNull(last.overallPlace());
            assertEquals("A", last.categoryCode());
            assertEquals(status, last.status());
        }
    }

    @Test
    void legacyKeepsExistingOverallAndNoCategoryPlace() throws Exception {
        var response = run(
                heat(
                        1,
                        entry("A", "30", null, null),
                        entry("B", "28", null, null)
                )
        );

        assertEquals(List.of(1, 2), overallPlaces(response));

        for (var entry : response.entries()) {
            assertNull(entry.categoryCode());
            assertNull(entry.categoryPlace());
        }
    }

    @Test
    void preservesUnitPlaces() throws Exception {
        var response = run(
                heat(1, entry("A", "30", "A", "G"))
        );

        assertEquals(
                Integer.valueOf(7),
                response.entries().get(0).unitPlace()
        );

        assertEquals(
                Integer.valueOf(1),
                response.entries().get(0).categoryPlace()
        );
    }

    @Test
    void rejectsMixedLegacyAndCategorizedPublishedAssignments() {
        var exception = assertThrows(
                BusinessException.class,
                () -> run(
                        heat(
                                1,
                                entry("A", "30", "A", "G"),
                                entry("B", "28", null, null)
                        )
                )
        );

        assertEquals(
                "results.mixed_category_assignments",
                exception.getCode()
        );
    }

    @Test
    void rejectsHalfPopulatedCategoryPair() {
        var exception = assertThrows(
                BusinessException.class,
                () -> run(heat(1, entry("A", "30", "A", null)))
        );

        assertEquals(
                "results.invalid_category_assignment",
                exception.getCode()
        );
    }

    @Test
    void rejectsInvalidCategoryCode() {
        var exception = assertThrows(
                BusinessException.class,
                () -> run(heat(1, entry("A", "30", "bad code", "G")))
        );

        assertEquals(
                "results.invalid_category_assignment",
                exception.getCode()
        );
    }

    @Test
    void invalidTimeStillFails() {
        var exception = assertThrows(
                BusinessException.class,
                () -> run(heat(1, entry("A", "1:75", "A", "G")))
        );

        assertEquals("results.invalid_raw_value", exception.getCode());
    }

    @Test
    void invalidPublishedStatusStillFails() {
        var pending = new PublicDisciplineResultsResponse.EntryView(
                null,
                "Pending",
                "Club",
                "30",
                "TIME",
                "PENDING",
                "A",
                "G"
        );

        var exception = assertThrows(
                BusinessException.class,
                () -> run(heat(1, pending))
        );

        assertEquals(
                "results.invalid_standings_status",
                exception.getCode()
        );
    }

    @Test
    void emptyPublishedListProducesEmptyStandings() throws Exception {
        assertTrue(run().entries().isEmpty());
    }

    @Test
    void repeatedReadsDoNotAccumulateCategoryCounters() throws Exception {
        run(
                heat(
                        1,
                        entry("A", "30", "A", "G"),
                        entry("B", "28", "A", "G")
                )
        );

        var firstRead = useCase.execute(event, discipline);
        var secondRead = useCase.execute(event, discipline);

        assertEquals(List.of(1, 2), categoryPlaces(firstRead));
        assertEquals(List.of(1, 2), categoryPlaces(secondRead));
        assertEquals(firstRead.entries(), secondRead.entries());
    }
}