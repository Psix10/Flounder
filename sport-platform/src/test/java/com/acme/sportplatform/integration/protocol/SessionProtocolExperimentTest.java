package com.acme.sportplatform.integration.protocol;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.competition.infrastructure.jpa.EventDisciplineEntity;
import com.acme.sportplatform.competition.infrastructure.jpa.EventDisciplineRepository;
import com.acme.sportplatform.events.infrastructure.persistence.entity.EventEntity;
import com.acme.sportplatform.events.infrastructure.persistence.repository.EventRepository;
import com.acme.sportplatform.identity.infrastructure.persistence.entity.ProfileEntity;
import com.acme.sportplatform.identity.infrastructure.persistence.entity.UserEntity;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.ProfileRepository;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.UserRepository;
import com.acme.sportplatform.identity.infrastructure.security.PlatformUserPrincipal;
import static com.acme.sportplatform.integration.protocol.SessionProtocolExperimentReport.LIMIT_NANOS;
import com.acme.sportplatform.integration.protocol.SessionProtocolExperimentReport.Measurement;
import static com.acme.sportplatform.integration.protocol.SessionProtocolExperimentReport.csv;
import static com.acme.sportplatform.integration.protocol.SessionProtocolExperimentReport.summarize;
import com.acme.sportplatform.protocol.application.GenerateSessionProtocolUseCase;
import com.acme.sportplatform.protocol.application.schedule.GenerateSessionProtocolCommand;
import com.acme.sportplatform.protocol.application.schedule.SessionProtocolGenerationResult;
import com.acme.sportplatform.protocol.domain.schedule.ProtocolScheduleModel.Heat;
import com.acme.sportplatform.protocol.domain.schedule.ProtocolScheduleModel.Input;
import com.acme.sportplatform.protocol.domain.schedule.ProtocolScheduleModel.Origin;
import com.acme.sportplatform.protocol.domain.schedule.ProtocolScheduleModel.Result;
import com.acme.sportplatform.protocol.domain.schedule.ProtocolScheduleModel.ScheduledHeat;
import com.acme.sportplatform.protocol.domain.schedule.ProtocolScheduleModel.Session;
import com.acme.sportplatform.protocol.domain.schedule.ProtocolScheduleModel.Status;
import com.acme.sportplatform.protocol.domain.schedule.ProtocolScheduleModel.TimingPolicy;
import com.acme.sportplatform.protocol.domain.schedule.ProtocolScheduleModel.Window;
import com.acme.sportplatform.protocol.domain.schedule.ProtocolScheduleValidator;
import com.acme.sportplatform.registrations.infrastructure.jpa.RegistrationEntity;
import com.acme.sportplatform.sports.infrastructure.jpa.DisciplineTemplateEntity;
import com.acme.sportplatform.sports.infrastructure.jpa.DisciplineTemplateRepository;
import com.acme.sportplatform.support.AbstractPostgresIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@AutoConfigureMockMvc
@EnabledIfSystemProperty(
        named = "protocol.session.experiment",
        matches = "true"
)
class SessionProtocolExperimentTest
        extends AbstractPostgresIntegrationTest {

    private static final UUID SPORT =
            UUID.fromString("10000000-0000-0000-0000-000000000001");

    private static final List<Integer> POSITIONS =
            List.of(4, 5, 3, 6, 2, 7, 1, 8);

    private static final List<String> SCENARIOS =
            List.of(
                    "baseline",
                    "nochange",
                    "seedchange",
                    "withdrawal",
                    "addition"
            );

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository users;
    @Autowired private ProfileRepository profiles;
    @Autowired private EventRepository events;
    @Autowired private DisciplineTemplateRepository templates;
    @Autowired private EventDisciplineRepository disciplines;
    @Autowired private GenerateSessionProtocolUseCase useCase;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;

    @PersistenceContext
    private EntityManager entityManager;

    private record Entry(
            UUID id,
            UUID profile,
            UUID discipline,
            int athleteIndex,
            boolean reserve,
            String originalMeta
    ) { }

    private record Fixture(
            UUID event,
            List<UUID> disciplines,
            UUID version,
            UUID session,
            Instant start,
            int baselineAthletes,
            PlatformUserPrincipal admin,
            List<Entry> entries
    ) { }

    private record Expected(
            Entry entry,
            BigDecimal metric
    ) { }

    private record Attempt(
            SessionProtocolGenerationResult result,
            long elapsedNanos,
            RuntimeException error
    ) { }

    private PlatformUserPrincipal actor(String role) {
        var u = new UserEntity();
        u.setEmail("session-exp-" + UUID.randomUUID() + "@example.com");
        u.setPasswordHash("test-password-hash");
        u.setStatus("active");
        u = users.saveAndFlush(u);

        return new PlatformUserPrincipal(
                u.getId(),
                u.getEmail(),
                "",
                "active",
                List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
    }

    private Fixture fixture(int entryCount) throws Exception {
        int n = entryCount / 2;
        var admin = actor("PLATFORM_ADMIN");

        var now = OffsetDateTime.now(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.MICROS);

        Instant start = now.plusDays(20)
                .withHour(9)
                .withMinute(30)
                .withSecond(0)
                .withNano(0)
                .toInstant();

        var event = new EventEntity();
        event.setId(UUID.randomUUID());
        event.setCreatedByUserId(admin.getUserId());
        event.setOrganizationId(UUID.randomUUID());
        event.setVenueId(UUID.randomUUID());
        event.setSportId(SPORT);
        event.setRegulationVersionId(UUID.randomUUID());
        event.setTitle("SESSION-" + entryCount + " synthetic experiment");
        event.setDescription("Test fixture only");
        event.setRegistrationOpenAt(now.minusDays(1));
        event.setRegistrationCloseAt(now.plusDays(10));
        event.setEventStartAt(
                start.minusSeconds(3600).atOffset(ZoneOffset.UTC)
        );
        event.setEventEndAt(
                start.plusSeconds(28800).atOffset(ZoneOffset.UTC)
        );
        event.setStatus("REGISTRATION_OPEN");
        event.setPublicSlug("session-exp-" + UUID.randomUUID());
        event.setSettingsJson("{}");
        event.setCreatedAt(now);
        event.setUpdatedAt(now);
        events.saveAndFlush(event);

        var template = new DisciplineTemplateEntity();
        template.setId(UUID.randomUUID());
        template.setSportId(SPORT);
        template.setCode("session-exp-" + UUID.randomUUID());
        template.setName("Synthetic freestyle");
        template.setCompetitionFormat("INDIVIDUAL");
        template.setUnitType("HEAT");
        template.setResultType("TIME");
        template.setRankingStrategy("ASC");
        template.setDefaultMeta("{}");
        templates.saveAndFlush(template);

        List<UUID> ds = new ArrayList<>();

        for (int j = 0; j < 2; j++) {
            var d = new EventDisciplineEntity();
            d.setId(UUID.randomUUID());
            d.setEventId(event.getId());
            d.setDisciplineTemplateId(template.getId());
            d.setCode("synthetic-" + j);
            d.setName("Synthetic discipline " + j);
            d.setCompetitionFormat("INDIVIDUAL");
            d.setUnitType("HEAT");
            d.setResultType("TIME");
            d.setRankingStrategy("ASC");
            d.setParticipantLimit(2000);
            d.setEntryFeeAmount(BigDecimal.ZERO);
            d.setEntryFeeCurrency("RUB");
            d.setStatus("PUBLISHED");
            d.setSettingsJson("{}");
            d.setCreatedAt(now);
            d.setUpdatedAt(now);
            disciplines.saveAndFlush(d);
            ds.add(d.getId());
        }

        List<Entry> es = new ArrayList<>();

        for (int i = 0; i < n + n / 5; i++) {
            var athlete = actor("PARTICIPANT");

            var p = new ProfileEntity();
            p.setUserId(athlete.getUserId());
            p.setFirstName("Athlete" + i);
            p.setLastName("Synthetic");
            p.setBirthDate(LocalDate.parse("2004-05-10"));
            p.setGender("MALE");
            p.setCountryCode("RU");
            p.setCity("Test");
            p.setClubName("Synthetic club " + i % 12);
            p.setSportMeta(Map.of("sportRank", "FIRST_CATEGORY"));
            p = profiles.saveAndFlush(p);

            for (int j = 0; j < ds.size(); j++) {
                var meta = json.createObjectNode();

                if (i % 10 != 0) {
                    meta.put(
                            "seedTimeSeconds",
                            BigDecimal.valueOf(
                                    25.0 + (i % 70) * 0.25 + j * 2.0
                            )
                    );
                }

                var r = new RegistrationEntity();
                r.setId(UUID.randomUUID());
                r.setEventId(event.getId());
                r.setEventDisciplineId(ds.get(j));
                r.setParticipantUserId(athlete.getUserId());
                r.setParticipantProfileId(p.getId());
                r.setStatus(i < n ? "CONFIRMED" : "SUBMITTED");

                r.setParticipantSnapshot(
                        json.writeValueAsString(
                                Map.of(
                                        "firstName", "Athlete" + i,
                                        "lastName", "Synthetic",
                                        "birthDate", "2004-05-10",
                                        "gender", "MALE",
                                        "clubName",
                                        "Synthetic club " + i % 12
                                )
                        )
                );

                r.setRegistrationMeta(meta.toString());
                r.setSubmittedAt(now);
                r.setCreatedAt(now);
                r.setUpdatedAt(now);

                new TransactionTemplate(transactions)
                        .executeWithoutResult(s -> {
                            entityManager.persist(r);
                            entityManager.flush();
                        });

                es.add(new Entry(
                        r.getId(),
                        p.getId(),
                        ds.get(j),
                        i,
                        i >= n,
                        meta.toString()
                ));
            }
        }

        ObjectNode request = (ObjectNode) json.readTree("""
                {
                  "sportId":"10000000-0000-0000-0000-000000000001",
                  "code":"placeholder",
                  "name":"Session experiment rules",
                  "competitionFormat":"INDIVIDUAL",
                  "unitType":"HEAT",
                  "blocks":[
                    {
                      "orderIndex":0,
                      "executorCode":"ELIGIBILITY_CONFIRMED_ONLY",
                      "config":{}
                    },
                    {
                      "orderIndex":1,
                      "executorCode":"SEED_BY_METRIC_ASC_NULLS_LAST",
                      "config":{"metricKey":"seedTimeSeconds"}
                    },
                    {
                      "orderIndex":2,
                      "executorCode":"GROUP_BY_CAPACITY",
                      "config":{
                        "capacity":8,
                        "unitLabelPrefix":"Heat",
                        "heatOrder":"SLOWEST_FIRST",
                        "partialHeat":"FIRST",
                        "ntPlacement":"WEAKEST"
                      }
                    },
                    {
                      "orderIndex":3,
                      "executorCode":"ASSIGN_CENTER_OUT",
                      "config":{
                        "allowedPositions":[1,2,3,4,5,6,7,8],
                        "positionOrder":[4,5,3,6,2,7,1,8]
                      }
                    }
                  ]
                }
                """);

        request.put("code", "session-exp-" + UUID.randomUUID());

        var created = json.readTree(
                mvc.perform(
                                post("/api/v1/protocol-rule-sets")
                                        .with(user(admin))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json.writeValueAsBytes(request)
                                        )
                        )
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString()
        );

        UUID version = UUID.fromString(
                created.path("ruleSetVersionId").asText()
        );

        mvc.perform(
                        post(
                                "/api/v1/protocol-rule-sets/{id}"
                                        + "/versions/{version}/publish",
                                created.path("id").asText(),
                                version
                        ).with(user(admin))
                )
                .andExpect(status().isOk());

        return new Fixture(
                event.getId(),
                List.copyOf(ds),
                version,
                UUID.randomUUID(),
                start,
                n,
                admin,
                List.copyOf(es)
        );
    }

    private Fixture bindPublishedRegulation(Fixture fixture)
            throws Exception {

        ObjectNode request = json.createObjectNode();
        request.putNull("sourceVersionId");
        request.put("sportId", SPORT.toString());
        request.put("code", "BOUND-SESSION-" + UUID.randomUUID());
        request.put(
                "name",
                "Session regulation binding integration test"
        );
        request.putNull("description");
        request.put("versionNo", 1);
        request.putNull("effectiveFrom");
        request.putNull("notes");

        request.putArray("conditions")
                .add("Integration test: use the bound protocol rules");

        ObjectNode protocol = request.putObject("protocol");
        protocol.put("capacity", 8);
        protocol.set(
                "positionOrder",
                json.valueToTree(POSITIONS)
        );
        protocol.put("metricKey", "seedTimeSeconds");
        protocol.put("heatOrder", "SLOWEST_FIRST");
        protocol.put("partialHeat", "FIRST");

        var response = mvc.perform(
                        post("/api/v1/regulation-configurations")
                                .with(user(fixture.admin()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsBytes(request))
                )
                .andExpect(status().is2xxSuccessful())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode document = json.readTree(response).path("document");

        String regulationIdText = document.path("id").asText();
        String boundVersionText = document.path("binding")
                .path("ruleVersionId")
                .asText();

        assertFalse(
                regulationIdText.isBlank(),
                "Regulation response must contain document.id"
        );
        assertFalse(
                boundVersionText.isBlank(),
                "Regulation response must contain binding.ruleVersionId"
        );

        UUID regulationVersionId = UUID.fromString(regulationIdText);
        UUID boundRuleVersionId = UUID.fromString(boundVersionText);

        assertNotEquals(
                fixture.version(),
                boundRuleVersionId,
                "Alternative rules must differ from the bound rules"
        );

        new TransactionTemplate(transactions)
                .executeWithoutResult(transaction -> {
                    int updated = jdbc.update(
                            """
                            UPDATE events
                            SET regulation_version_id = ?
                            WHERE id = ?
                            """,
                            regulationVersionId,
                            fixture.event()
                    );

                    assertEquals(1, updated);
                    entityManager.clear();
                });

        return new Fixture(
                fixture.event(),
                fixture.disciplines(),
                boundRuleVersionId,
                fixture.session(),
                fixture.start(),
                fixture.baselineAthletes(),
                fixture.admin(),
                fixture.entries()
        );
    }

    private GenerateSessionProtocolCommand command(
            Fixture f,
            boolean regenerate,
            long windowSeconds
    ) {
        return new GenerateSessionProtocolCommand(
                f.event(),
                regenerate,
                new TimingPolicy(
                        Duration.ofSeconds(600),
                        Duration.ofSeconds(30),
                        Origin.EXPERIMENTAL,
                        "Synthetic experiment: not an official regulation"
                ),
                List.of(
                        new Session(
                                f.session(),
                                new Window(
                                        f.start(),
                                        f.start().plusSeconds(windowSeconds)
                                ),
                                List.of()
                        )
                ),
                f.disciplines().stream()
                        .map(d -> new GenerateSessionProtocolCommand.Discipline(
                                d,
                                f.version(),
                                f.session(),
                                Duration.ofSeconds(120),
                                "Explicit synthetic planning duration"
                        ))
                        .toList()
        );
    }

    private Attempt invoke(
            Fixture f,
            GenerateSessionProtocolCommand command
    ) {
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        f.admin(),
                        "",
                        f.admin().getAuthorities()
                )
        );

        SecurityContextHolder.setContext(context);

        try {
            long started = System.nanoTime();

            try {
                var result = useCase.execute(command);

                return new Attempt(
                        result,
                        System.nanoTime() - started,
                        null
                );
            } catch (RuntimeException ex) {
                return new Attempt(
                        null,
                        System.nanoTime() - started,
                        ex
                );
            }
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private SessionProtocolGenerationResult successful(Attempt attempt) {
        if (attempt.error() != null) {
            throw attempt.error();
        }

        return Objects.requireNonNull(attempt.result());
    }

    private boolean exists(Fixture f) {
        return jdbc.queryForObject(
                """
                SELECT count(*)
                FROM competition_units
                WHERE event_discipline_id IN (?, ?)
                """,
                Long.class,
                f.disciplines().get(0),
                f.disciplines().get(1)
        ) > 0;
    }

    private void restore(Fixture f, String scenario) {
        List<Object[]> args = new ArrayList<>();

        var now = OffsetDateTime.now(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.MICROS);

        for (var entry : f.entries()) {
            String state = entry.reserve()
                    ? "SUBMITTED"
                    : "CONFIRMED";

            String meta = entry.originalMeta();

            if (scenario.equals("withdrawal")
                    && !entry.reserve()
                    && entry.athleteIndex() < f.baselineAthletes() / 5) {
                state = "REJECTED";
            }

            if (scenario.equals("addition") && entry.reserve()) {
                state = "CONFIRMED";
            }

            if (scenario.equals("seedchange")
                    && !entry.reserve()
                    && entry.athleteIndex() < f.baselineAthletes() / 5) {
                meta = "{\"seedTimeSeconds\":99.99}";
            }

            args.add(new Object[]{
                    state,
                    meta,
                    now,
                    entry.id()
            });
        }

        new TransactionTemplate(transactions)
                .executeWithoutResult(s -> jdbc.batchUpdate(
                        """
                        UPDATE registrations
                        SET status = ?,
                            registration_meta = CAST(? AS jsonb),
                            updated_at = ?
                        WHERE id = ?
                        """,
                        args
                ));
    }

    private void clearProtocols(Fixture f) {
        new TransactionTemplate(transactions)
                .executeWithoutResult(s -> {
                    assertEquals(
                            0L,
                            jdbc.queryForObject(
                                    """
                                    SELECT count(*)
                                    FROM results r
                                    JOIN competition_unit_entries e
                                      ON e.id = r.competition_unit_entry_id
                                    JOIN competition_units u
                                      ON u.id = e.competition_unit_id
                                    WHERE u.event_discipline_id IN (?, ?)
                                    """,
                                    Long.class,
                                    f.disciplines().get(0),
                                    f.disciplines().get(1)
                            ).longValue()
                    );

                    jdbc.update(
                            """
                            DELETE FROM competition_unit_entries e
                            USING competition_units u
                            WHERE e.competition_unit_id = u.id
                              AND u.event_discipline_id IN (?, ?)
                            """,
                            f.disciplines().get(0),
                            f.disciplines().get(1)
                    );

                    jdbc.update(
                            """
                            DELETE FROM competition_units
                            WHERE event_discipline_id IN (?, ?)
                            """,
                            f.disciplines().get(0),
                            f.disciplines().get(1)
                    );

                    jdbc.update(
                            """
                            DELETE FROM start_protocol_snapshots
                            WHERE event_discipline_id IN (?, ?)
                            """,
                            f.disciplines().get(0),
                            f.disciplines().get(1)
                    );

                    jdbc.update(
                            """
                            DELETE FROM session_protocol_snapshots
                            WHERE event_id = ?
                            """,
                            f.event()
                    );
                });
    }

    private SessionProtocolGenerationResult setup(
            Fixture f,
            String scenario
    ) {
        restore(f, "baseline");

        if (scenario.equals("baseline")) {
            clearProtocols(f);
            return null;
        }

        var baseline = successful(
                invoke(f, command(f, exists(f), 21600))
        );

        if (!scenario.equals("nochange")) {
            restore(f, scenario);
        }

        return baseline;
    }

    private Map<UUID, Expected> expected(Fixture f)
            throws Exception {

        Map<UUID, Entry> known = f.entries().stream()
                .collect(Collectors.toMap(Entry::id, e -> e));

        Map<UUID, Expected> result = new HashMap<>();

        for (var row : jdbc.queryForList(
                """
                SELECT id, registration_meta::text AS meta
                FROM registrations
                WHERE event_id = ?
                  AND status = 'CONFIRMED'
                """,
                f.event()
        )) {
            UUID id = (UUID) row.get("id");

            var value = json.readTree((String) row.get("meta"))
                    .path("seedTimeSeconds");

            result.put(
                    id,
                    new Expected(
                            Objects.requireNonNull(known.get(id)),
                            value.isNumber()
                                    ? value.decimalValue()
                                    : null
                    )
            );
        }

        return result;
    }

    private List<String> signature(
            SessionProtocolGenerationResult result
    ) {
        List<String> values = new ArrayList<>();

        for (var discipline : result.disciplines()) {
            for (var unit : discipline.units()) {
                for (var assignment : unit.assignments()) {
                    values.add(
                            discipline.eventDisciplineId()
                                    + ":" + unit.sequenceNumber()
                                    + ":" + assignment.registrationId()
                                    + ":" + assignment.laneOrPosition()
                    );
                }
            }
        }

        Collections.sort(values);
        return values;
    }

    private JsonNode validate(
            Fixture f,
            SessionProtocolGenerationResult result,
            Map<UUID, Expected> expected,
            String scenario,
            SessionProtocolGenerationResult baseline
    ) throws Exception {

        var inputJson = json.readTree(
                jdbc.queryForObject(
                        """
                        SELECT input_json::text
                        FROM session_protocol_snapshots
                        WHERE id = ?
                        """,
                        String.class,
                        result.snapshotId()
                )
        );

        var protocol = SessionProtocolSnapshotJsonAssertions.parse(
                json,
                jdbc.queryForObject(
                        """
                        SELECT protocol_json::text
                        FROM session_protocol_snapshots
                        WHERE id = ?
                        """,
                        String.class,
                        result.snapshotId()
                )
        );

        SessionProtocolSnapshotJsonAssertions.assertSnapshotMatches(
                json,
                result,
                protocol
        );

        Input input = json.treeToValue(
                inputJson.path("schedulerInput"),
                Input.class
        );

        var rebuilt = new Result(
                Status.SCHEDULED,
                input.policy().origin(),
                input.policy().sourceDescription(),
                result.schedule().stream()
                        .map(h -> new ScheduledHeat(
                                h.temporaryHeatId(),
                                h.sessionId(),
                                h.disciplineId(),
                                h.start(),
                                h.end()
                        ))
                        .toList(),
                null
        );

        assertTrue(
                new ProtocolScheduleValidator()
                        .validate(input, rebuilt)
                        .isEmpty(),
                "Independent timetable validation failed"
        );

        var heatByTemporary = input.heats().stream()
                .collect(Collectors.toMap(Heat::id, h -> h));

        var boundBySaved = result.schedule().stream()
                .collect(Collectors.toMap(h -> h.savedHeatId(), h -> h));

        assertEquals(
                new HashSet<>(f.disciplines()),
                result.disciplines().stream()
                        .map(d -> d.eventDisciplineId())
                        .collect(Collectors.toSet())
        );

        Set<UUID> actualRegistrations = new HashSet<>();
        Set<UUID> actualUnits = new HashSet<>();

        for (var discipline : result.disciplines()) {
            int sequence = 0;

            for (var unit : discipline.units()) {
                assertEquals(++sequence, unit.sequenceNumber());
                assertTrue(actualUnits.add(unit.competitionUnitId()));
                assertFalse(unit.assignments().isEmpty());
                assertTrue(unit.assignments().size() <= 8);

                var bound = Objects.requireNonNull(
                        boundBySaved.get(unit.competitionUnitId())
                );

                assertEquals(
                        discipline.eventDisciplineId(),
                        bound.disciplineId()
                );

                var heat = Objects.requireNonNull(
                        heatByTemporary.get(bound.temporaryHeatId())
                );

                Set<UUID> profilesInHeat = new HashSet<>();
                Set<Integer> lanes = new HashSet<>();

                for (int i = 0; i < unit.assignments().size(); i++) {
                    var assignment = unit.assignments().get(i);

                    assertTrue(
                            actualRegistrations.add(
                                    assignment.registrationId()
                            )
                    );

                    var source = Objects.requireNonNull(
                            expected.get(assignment.registrationId()),
                            "Ineligible or unknown registration assigned"
                    );

                    assertEquals(
                            discipline.eventDisciplineId(),
                            source.entry().discipline()
                    );
                    assertTrue(
                            profilesInHeat.add(source.entry().profile())
                    );
                    assertTrue(
                            lanes.add(assignment.laneOrPosition())
                    );
                    assertEquals(
                            POSITIONS.get(i),
                            assignment.laneOrPosition()
                    );

                    if (source.metric() == null) {
                        assertNull(assignment.seedMetric());
                    } else {
                        assertEquals(
                                0,
                                source.metric().compareTo(
                                        assignment.seedMetric()
                                )
                        );
                    }
                }

                assertEquals(
                        profilesInHeat,
                        new HashSet<>(heat.athleteIds())
                );
                assertEquals(
                        profilesInHeat.size(),
                        heat.athleteIds().size()
                );
            }
        }

        assertEquals(expected.keySet(), actualRegistrations);
        assertEquals(boundBySaved.keySet(), actualUnits);

        var dbRows = jdbc.queryForList(
                """
                SELECT u.id,
                       u.event_discipline_id,
                       u.sequence_number,
                       u.scheduled_at,
                       e.registration_id,
                       e.lane_or_position
                FROM competition_units u
                JOIN competition_unit_entries e
                  ON e.competition_unit_id = u.id
                WHERE u.event_discipline_id IN (?, ?)
                """,
                f.disciplines().get(0),
                f.disciplines().get(1)
        );

        Map<UUID, Instant> databaseTimes = new HashMap<>();

        jdbc.query(
                """
                SELECT id, scheduled_at
                FROM competition_units
                WHERE event_discipline_id IN (?, ?)
                """,
                rs -> {
                    databaseTimes.put(
                            rs.getObject("id", UUID.class),
                            rs.getObject(
                                    "scheduled_at",
                                    OffsetDateTime.class
                            ).toInstant()
                    );
                },
                f.disciplines().get(0),
                f.disciplines().get(1)
        );

        assertEquals(actualUnits, databaseTimes.keySet());

        Set<UUID> databaseRegistrations = new HashSet<>();
        Set<String> databaseSignature = new HashSet<>();
        Set<String> expectedSignature = new HashSet<>();

        for (var row : dbRows) {
            UUID id = (UUID) row.get("id");

            var bound = Objects.requireNonNull(boundBySaved.get(id));
            Instant persisted = Objects.requireNonNull(
                    databaseTimes.get(id)
            );

            assertEquals(bound.start(), persisted);
            assertEquals(
                    bound.disciplineId(),
                    row.get("event_discipline_id")
            );
            assertEquals(
                    bound.sequenceNumber(),
                    ((Number) row.get("sequence_number")).intValue()
            );
            assertTrue(
                    databaseRegistrations.add(
                            (UUID) row.get("registration_id")
                    )
            );

            databaseSignature.add(
                    id
                            + ":" + row.get("registration_id")
                            + ":" + row.get("lane_or_position")
            );
        }

        for (var discipline : result.disciplines()) {
            for (var unit : discipline.units()) {
                for (var assignment : unit.assignments()) {
                    expectedSignature.add(
                            unit.competitionUnitId()
                                    + ":" + assignment.registrationId()
                                    + ":" + assignment.laneOrPosition()
                    );
                }
            }
        }

        assertEquals(expectedSignature, databaseSignature);
        assertEquals(expected.keySet(), databaseRegistrations);

        if (scenario.equals("nochange")) {
            assertEquals(signature(baseline), signature(result));
        }

        if (scenario.equals("seedchange")) {
            assertNotEquals(
                    signature(baseline),
                    signature(result),
                    "Seed-change control must actually change assignments"
            );
        }

        var artifact = json.createObjectNode();
        artifact.set("input", inputJson);
        artifact.set("protocol", protocol);
        return artifact;
    }

    private Map<String, List<Map<String, Object>>> state(Fixture f) {
        return Map.of(
                "units",
                jdbc.queryForList(
                        """
                        SELECT *
                        FROM competition_units
                        WHERE event_discipline_id IN (?, ?)
                        ORDER BY id
                        """,
                        f.disciplines().get(0),
                        f.disciplines().get(1)
                ),
                "entries",
                jdbc.queryForList(
                        """
                        SELECT e.*
                        FROM competition_unit_entries e
                        JOIN competition_units u
                          ON u.id = e.competition_unit_id
                        WHERE u.event_discipline_id IN (?, ?)
                        ORDER BY e.id
                        """,
                        f.disciplines().get(0),
                        f.disciplines().get(1)
                ),
                "disciplineSnapshots",
                jdbc.queryForList(
                        """
                        SELECT *
                        FROM start_protocol_snapshots
                        WHERE event_discipline_id IN (?, ?)
                        ORDER BY id
                        """,
                        f.disciplines().get(0),
                        f.disciplines().get(1)
                ),
                "sessionSnapshots",
                jdbc.queryForList(
                        """
                        SELECT *
                        FROM session_protocol_snapshots
                        WHERE event_id = ?
                        ORDER BY revision_no
                        """,
                        f.event()
                )
        );
    }

    private void write(Path path, Object value) throws Exception {
        Files.createDirectories(path.getParent());
        Files.writeString(
                path,
                json.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(value),
                StandardCharsets.UTF_8
        );
    }

    private Map<String, String> sourceHashes() throws Exception {
        Map<String, String> result = new TreeMap<>();
        List<Path> paths = new ArrayList<>();

        try (var stream = Files.walk(Path.of("src", "main", "java"))) {
            paths.addAll(
                    stream.filter(p -> p.toString().endsWith(".java"))
                            .toList()
            );
        }

        paths.add(Path.of("pom.xml"));

        try (var stream = Files.walk(
                Path.of(
                        "src",
                        "test",
                        "java",
                        "com",
                        "acme",
                        "sportplatform",
                        "integration",
                        "protocol"
                )
        )) {
            paths.addAll(
                    stream.filter(
                            p -> p.getFileName().toString()
                                    .startsWith("SessionProtocolExperiment")
                                    && p.toString().endsWith(".java")
                    ).toList()
            );
        }

        for (var path : paths) {
            result.put(
                    path.toString().replace('\\', '/'),
                    HexFormat.of().formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(Files.readAllBytes(path))
                    )
            );
        }

        return result;
    }

    private void reports(
            Path out,
            List<Measurement> rows,
            int runs
    ) throws Exception {

        var summaries = SCENARIOS.stream()
                .filter(s -> rows.stream()
                        .anyMatch(r -> r.scenario().equals(s)))
                .map(s -> summarize(s, rows))
                .toList();

        StringBuilder csv = new StringBuilder(
                "scenario,iteration,elapsed_ns,elapsed_ms,"
                        + "correct,correct_within_5s,error\n"
        );

        for (var r : rows) {
            csv.append(csv(r.scenario())).append(',')
                    .append(r.iteration()).append(',')
                    .append(r.elapsedNanos()).append(',')
                    .append(r.elapsedNanos() / 1_000_000.0).append(',')
                    .append(r.correct()).append(',')
                    .append(
                            r.correct()
                                    && r.elapsedNanos() <= LIMIT_NANOS
                    )
                    .append(',')
                    .append(csv(r.error()))
                    .append('\n');
        }

        Files.writeString(
                out.resolve("measurements.csv"),
                csv,
                StandardCharsets.UTF_8
        );

        StringBuilder summaryCsv = new StringBuilder(
                "scenario,runs,correct_runs,correct_within_5s,"
                        + "qualified_share,p50_ms,p95_ms,max_ms,assessment\n"
        );

        for (var s : summaries) {
            summaryCsv.append(s.scenario()).append(',')
                    .append(s.runs()).append(',')
                    .append(s.correctRuns()).append(',')
                    .append(s.correctWithin5s()).append(',')
                    .append(s.qualifiedShare()).append(',')
                    .append(s.p50Ms()).append(',')
                    .append(s.p95Ms()).append(',')
                    .append(s.maxMs()).append(',')
                    .append(s.assessment()).append('\n');
        }

        Files.writeString(
                out.resolve("summary.csv"),
                summaryCsv,
                StandardCharsets.UTF_8
        );

        var report = json.createObjectNode();

        boolean complete = rows.size() == runs * SCENARIOS.size();
        boolean allCorrect = !rows.isEmpty()
                && rows.stream().allMatch(Measurement::correct);

        report.put("complete", complete);
        report.put(
                "expectedMeasurements",
                runs * SCENARIOS.size()
        );
        report.put("actualMeasurements", rows.size());
        report.put("allMeasuredResultsCorrect", allCorrect);

        report.put(
                "overallAssessment",
                !complete
                        ? "INCOMPLETE"
                        : !allCorrect
                                ? "INVALID_RESULTS"
                                : runs < 30
                                        ? "EXPLORATORY"
                                        : summaries.stream().allMatch(
                                                s -> s.assessment()
                                                        .equals("MET")
                                        )
                                                ? "MET"
                                                : "NOT_MET"
        );

        report.put(
                "percentileMethod",
                "nearest-rank; each scenario separately; "
                        + "errors remain in denominator"
        );
        report.set("scenarios", json.valueToTree(summaries));

        write(out.resolve("summary.json"), report);
    }

    @Test
    void rejectsRuleSubstitutionWithoutChangingSavedSession()
            throws Exception {

        var originalFixture = fixture(60);
        var boundFixture = bindPublishedRegulation(originalFixture);

        var generated = successful(
                invoke(
                        boundFixture,
                        command(boundFixture, false, 21600)
                )
        );

        validate(
                boundFixture,
                generated,
                expected(boundFixture),
                "baseline",
                null
        );

        Long savedBoundSnapshots = jdbc.queryForObject(
                """
                SELECT count(*)
                FROM start_protocol_snapshots
                WHERE event_discipline_id IN (?, ?)
                  AND rule_set_version_id = ?
                """,
                Long.class,
                boundFixture.disciplines().get(0),
                boundFixture.disciplines().get(1),
                boundFixture.version()
        );

        assertEquals(
                2L,
                Objects.requireNonNull(savedBoundSnapshots).longValue(),
                "Both discipline snapshots must reference the bound rules"
        );

        var before = state(boundFixture);

        var rejected = invoke(
                originalFixture,
                command(originalFixture, true, 21600)
        );

        assertNotNull(
                rejected.error(),
                "Regeneration with alternative rules must be rejected"
        );

        assertInstanceOf(
                BusinessException.class,
                rejected.error()
        );

        assertEquals(
                "Use the rule version bound to the event regulation",
                rejected.error().getMessage()
        );

        assertEquals(
                before,
                state(boundFixture),
                "Rejected regeneration must not change units, "
                        + "entries or protocol snapshots"
        );
    }

    @Test
    void measureFullTransactionalScenario() throws Exception {
        int entryCount = Integer.getInteger(
                "protocol.session.experiment.entries",
                60
        );
        int runs = Integer.getInteger(
                "protocol.session.experiment.runs",
                3
        );
        int warmups = Integer.getInteger(
                "protocol.session.experiment.warmups",
                1
        );

        assertTrue(Set.of(60, 200, 500).contains(entryCount));
        assertTrue(runs >= 1 && runs <= 1000);
        assertTrue(warmups >= 0 && warmups <= 20);

        Path out = Path.of(
                "target",
                "protocol-session-experiments",
                "SESSION-" + entryCount
                        + "-" + Instant.now().toString().replace(':', '-')
                        + "-" + UUID.randomUUID()
        );

        Files.createDirectories(out);
        List<Measurement> rows = new ArrayList<>();

        try {
            var f = bindPublishedRegulation(fixture(entryCount));

            var manifest = json.createObjectNode();
            manifest.put("schemaVersion", 1);
            manifest.put("dataset", "SESSION-" + entryCount);
            manifest.put("baselineEntries", entryCount);
            manifest.put(
                    "baselineUniqueAthletes",
                    f.baselineAthletes()
            );
            manifest.put("disciplines", 2);
            manifest.put("changeFraction", 0.2);
            manifest.put("reserveEntries", entryCount / 5);
            manifest.put("runsPerScenario", runs);
            manifest.put("warmupsPerScenario", warmups);
            manifest.put(
                    "javaVersion",
                    System.getProperty("java.version")
            );
            manifest.put("os", System.getProperty("os.name"));
            manifest.put("arch", System.getProperty("os.arch"));
            manifest.put(
                    "availableProcessors",
                    Runtime.getRuntime().availableProcessors()
            );
            manifest.put(
                    "maxHeapBytes",
                    Runtime.getRuntime().maxMemory()
            );
            manifest.put(
                    "postgresVersion",
                    jdbc.queryForObject(
                            "SHOW server_version",
                            String.class
                    )
            );
            manifest.put(
                    "sourceRevision",
                    System.getProperty(
                            "protocol.session.experiment.sourceRevision",
                            "UNRECORDED"
                    )
            );
            manifest.put(
                    "workingTreeDirty",
                    System.getProperty(
                            "protocol.session.experiment.workingTreeDirty",
                            "UNRECORDED"
                    )
            );
            manifest.put(
                    "measurementBoundary",
                    "Spring useCase.execute including transaction commit; "
                            + "excludes fixture setup, external validation "
                            + "and file IO; no HTTP"
            );
            manifest.set(
                    "sourceSha256",
                    json.valueToTree(sourceHashes())
            );

            var fixtureNode = json.createObjectNode();
            fixtureNode.put("eventId", f.event().toString());
            fixtureNode.put("sessionId", f.session().toString());
            fixtureNode.put(
                    "ruleSetVersionId",
                    f.version().toString()
            );
            fixtureNode.set(
                    "disciplineIds",
                    json.valueToTree(f.disciplines())
            );
            fixtureNode.set(
                    "entries",
                    json.valueToTree(f.entries())
            );

            manifest.set("fixture", fixtureNode);
            write(out.resolve("manifest.json"), manifest);

            restore(f, "baseline");
            successful(invoke(f, command(f, false, 21600)));

            var before = state(f);
            var rejected = invoke(f, command(f, true, 60));

            assertNotNull(rejected.error());
            assertInstanceOf(
                    BusinessException.class,
                    rejected.error()
            );
            assertTrue(
                    String.valueOf(rejected.error().getMessage())
                            .contains("Programme does not fit")
            );
            assertEquals(before, state(f));

            write(
                    out.resolve("negative-window-control.json"),
                    Map.of(
                            "passed", true,
                            "windowSeconds", 60,
                            "dataUnchanged", true
                    )
            );

            for (String scenario : SCENARIOS) {
                for (int i = 0; i < warmups; i++) {
                    var baseline = setup(f, scenario);
                    var expected = expected(f);

                    var result = successful(
                            invoke(
                                    f,
                                    command(
                                            f,
                                            !scenario.equals("baseline"),
                                            21600
                                    )
                            )
                    );

                    validate(
                            f,
                            result,
                            expected,
                            scenario,
                            baseline
                    );
                }

                for (int iteration = 1;
                     iteration <= runs;
                     iteration++) {

                    var baseline = setup(f, scenario);
                    var expected = expected(f);

                    var cmd = command(
                            f,
                            !scenario.equals("baseline"),
                            21600
                    );

                    var attempt = invoke(f, cmd);
                    boolean correct = false;
                    String error = "";

                    Path detail = out.resolve(scenario).resolve(
                            String.format(
                                    Locale.ROOT,
                                    "run-%03d.json",
                                    iteration
                            )
                    );

                    try {
                        var result = successful(attempt);

                        var artifact = validate(
                                f,
                                result,
                                expected,
                                scenario,
                                baseline
                        );

                        write(detail, artifact);
                        correct = true;

                    } catch (Exception | AssertionError ex) {
                        error = ex.getClass().getName()
                                + ": " + String.valueOf(ex.getMessage());

                        var failure = json.createObjectNode();
                        failure.put("error", error);
                        failure.set(
                                "command",
                                json.valueToTree(cmd)
                        );

                        if (attempt.result() != null) {
                            failure.set(
                                    "result",
                                    json.valueToTree(attempt.result())
                            );

                            try {
                                failure.set(
                                        "snapshotInput",
                                        json.readTree(
                                                jdbc.queryForObject(
                                                        """
                                                        SELECT input_json::text
                                                        FROM session_protocol_snapshots
                                                        WHERE id = ?
                                                        """,
                                                        String.class,
                                                        attempt.result()
                                                                .snapshotId()
                                                )
                                        )
                                );

                                failure.set(
                                        "snapshotProtocol",
                                        json.readTree(
                                                jdbc.queryForObject(
                                                        """
                                                        SELECT protocol_json::text
                                                        FROM session_protocol_snapshots
                                                        WHERE id = ?
                                                        """,
                                                        String.class,
                                                        attempt.result()
                                                                .snapshotId()
                                                )
                                        )
                                );

                            } catch (Exception readError) {
                                failure.put(
                                        "artifactReadError",
                                        String.valueOf(readError)
                                );
                            }
                        }

                        write(detail, failure);
                    }

                    rows.add(new Measurement(
                            scenario,
                            iteration,
                            attempt.elapsedNanos(),
                            correct,
                            error
                    ));

                    reports(out, rows, runs);
                }
            }

        } catch (Exception | AssertionError ex) {
            write(
                    out.resolve("aborted.json"),
                    Map.of(
                            "error",
                            ex.getClass().getName()
                                    + ": "
                                    + String.valueOf(ex.getMessage())
                    )
            );

            throw ex;

        } finally {
            reports(out, rows, runs);
            System.out.println(
                    "Session experiment artifacts: "
                            + out.toAbsolutePath()
            );
        }

        assertEquals(
                runs * SCENARIOS.size(),
                rows.size()
        );

        assertTrue(
                rows.stream().allMatch(Measurement::correct),
                "Some results failed validation; inspect experiment artifacts"
        );
    }

    @Test
        void rejectsPublishedSecondDisciplineWithoutChangingWholeSession()
                throws Exception {

        var f = bindPublishedRegulation(fixture(60));

        var generated = successful(
                invoke(f, command(f, false, 21600))
        );

        validate(
                f,
                generated,
                expected(f),
                "baseline",
                null
        );

        UUID firstDiscipline = f.disciplines().get(0);
        UUID secondDiscipline = f.disciplines().get(1);

        assertTrue(
                jdbc.queryForObject(
                        """
                        SELECT count(*)
                        FROM competition_units
                        WHERE event_discipline_id = ?
                        AND status = 'DRAFT'
                        """,
                        Long.class,
                        firstDiscipline
                ) > 0
        );

        int published = jdbc.update(
                """
                UPDATE competition_units
                SET status = 'PUBLISHED',
                        version = version + 1
                WHERE event_discipline_id = ?
                """,
                secondDiscipline
        );

        assertTrue(published > 0);

        var before = state(f);

        var rejected = invoke(
                f,
                command(f, true, 21600)
        );

        assertNotNull(rejected.error());

        assertInstanceOf(
                BusinessException.class,
                rejected.error()
        );

        assertEquals(
                "Published units cannot be regenerated",
                rejected.error().getMessage()
        );

        assertEquals(
                before,
                state(f),
                "Rejected batch regeneration must preserve both "
                        + "disciplines and all protocol snapshots"
        );
        }

        @Test
        void rejectsResultInSecondDisciplineWithoutChangingWholeSession()
                throws Exception {

        var f = bindPublishedRegulation(fixture(60));

        var generated = successful(
                invoke(f, command(f, false, 21600))
        );

        validate(
                f,
                generated,
                expected(f),
                "baseline",
                null
        );

        UUID secondDiscipline = f.disciplines().get(1);

        UUID entryId = jdbc.queryForObject(
                """
                SELECT e.id
                FROM competition_unit_entries e
                JOIN competition_units u
                ON u.id = e.competition_unit_id
                WHERE u.event_discipline_id = ?
                ORDER BY u.sequence_number, e.id
                LIMIT 1
                """,
                UUID.class,
                secondDiscipline
        );

        assertNotNull(entryId);

        mvc.perform(
                        post("/api/v1/results")
                                .with(user(f.admin()))
                                .param("resultType", "TIME")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsBytes(
                                        Map.of(
                                                "competitionUnitEntryId",
                                                entryId,
                                                "rawValue",
                                                "28.11",
                                                "status",
                                                "PENDING"
                                        )
                                ))
                )
                .andExpect(status().isOk());

        var before = state(f);

        var resultsBefore = jdbc.queryForList(
                """
                SELECT r.*
                FROM results r
                JOIN competition_unit_entries e
                ON e.id = r.competition_unit_entry_id
                JOIN competition_units u
                ON u.id = e.competition_unit_id
                WHERE u.event_discipline_id = ?
                ORDER BY r.id
                """,
                secondDiscipline
        );

        assertEquals(1, resultsBefore.size());

        var rejected = invoke(
                f,
                command(f, true, 21600)
        );

        assertNotNull(rejected.error());

        assertInstanceOf(
                BusinessException.class,
                rejected.error()
        );

        assertEquals(
                "Cannot regenerate units containing any results",
                rejected.error().getMessage()
        );

        assertEquals(
                before,
                state(f),
                "Rejected batch regeneration must preserve both "
                        + "disciplines and all protocol snapshots"
        );

        var resultsAfter = jdbc.queryForList(
                """
                SELECT r.*
                FROM results r
                JOIN competition_unit_entries e
                ON e.id = r.competition_unit_entry_id
                JOIN competition_units u
                ON u.id = e.competition_unit_id
                WHERE u.event_discipline_id = ?
                ORDER BY r.id
                """,
                secondDiscipline
        );

        assertEquals(
                resultsBefore,
                resultsAfter,
                "Existing results must remain unchanged"
        );
        }
}