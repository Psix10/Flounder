package com.acme.sportplatform.integration.protocol;

import static com.acme.sportplatform.protocol.domain.schedule.ProtocolScheduleModel.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
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
import com.acme.sportplatform.protocol.CompetitionUnitProtocolAccess;
import com.acme.sportplatform.protocol.application.GenerateSessionProtocolUseCase;
import com.acme.sportplatform.protocol.application.schedule.GenerateSessionProtocolCommand;
import com.acme.sportplatform.protocol.application.schedule.SessionProtocolGenerationResult;
import com.acme.sportplatform.registrations.infrastructure.jpa.RegistrationEntity;
import com.acme.sportplatform.sports.infrastructure.jpa.DisciplineTemplateEntity;
import com.acme.sportplatform.sports.infrastructure.jpa.DisciplineTemplateRepository;
import com.acme.sportplatform.support.AbstractPostgresIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@AutoConfigureMockMvc
class SessionProtocolTransactionIntegrationTest
        extends AbstractPostgresIntegrationTest {

    private static final UUID SPORT = UUID.fromString(
            "10000000-0000-0000-0000-000000000001"
    );

    private static final Instant T =
            Instant.parse("2026-11-22T06:30:00Z");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private UserRepository users;

    @Autowired
    private ProfileRepository profiles;

    @Autowired
    private EventRepository events;

    @Autowired
    private DisciplineTemplateRepository templates;

    @Autowired
    private EventDisciplineRepository disciplines;

    @Autowired
    private GenerateSessionProtocolUseCase useCase;

    @Autowired
    private CompetitionUnitProtocolAccess protocolAccess;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactions;

    @PersistenceContext
    private EntityManager entityManager;

    private record Fixture(
            UUID event,
            UUID first,
            UUID second,
            UUID version,
            UUID session,
            PlatformUserPrincipal admin
    ) { }

    private PlatformUserPrincipal actor(String role) {
        var userEntity = new UserEntity();

        userEntity.setEmail(
                "session-" + UUID.randomUUID() + "@example.com"
        );
        userEntity.setPasswordHash("test-password-hash");
        userEntity.setStatus("active");

        userEntity = users.saveAndFlush(userEntity);

        return new PlatformUserPrincipal(
                userEntity.getId(),
                userEntity.getEmail(),
                "",
                "active",
                List.of(
                        new SimpleGrantedAuthority("ROLE_" + role)
                )
        );
    }

    private Fixture fixture() throws Exception {
        var admin = actor("PLATFORM_ADMIN");
        var athlete = actor("PARTICIPANT");

        var profile = new ProfileEntity();
        profile.setUserId(athlete.getUserId());
        profile.setFirstName("Ivan");
        profile.setLastName("Petrov");
        profile.setBirthDate(LocalDate.parse("2004-05-10"));
        profile.setGender("MALE");
        profile.setCountryCode("RU");
        profile.setCity("Moscow");
        profile.setClubName("Test");
        profile.setSportMeta(
                Map.of("sportRank", "FIRST_CATEGORY")
        );

        var savedProfile = profiles.saveAndFlush(profile);
        var now = OffsetDateTime.now(ZoneOffset.UTC);

        var event = new EventEntity();
        event.setId(UUID.randomUUID());
        event.setCreatedByUserId(admin.getUserId());
        event.setOrganizationId(UUID.randomUUID());
        event.setVenueId(UUID.randomUUID());
        event.setSportId(SPORT);
        event.setRegulationVersionId(UUID.randomUUID());
        event.setTitle("Session transaction test");
        event.setDescription("Fixture");
        event.setRegistrationOpenAt(now.minusDays(1));
        event.setRegistrationCloseAt(now.plusDays(10));
        event.setEventStartAt(now.plusDays(20));
        event.setEventEndAt(now.plusDays(21));
        event.setStatus("REGISTRATION_OPEN");
        event.setPublicSlug("session-" + UUID.randomUUID());
        event.setSettingsJson("{}");
        event.setCreatedAt(now);
        event.setUpdatedAt(now);

        events.saveAndFlush(event);

        var template = new DisciplineTemplateEntity();
        template.setId(UUID.randomUUID());
        template.setSportId(SPORT);
        template.setCode("session-" + UUID.randomUUID());
        template.setName("Freestyle");
        template.setCompetitionFormat("INDIVIDUAL");
        template.setUnitType("HEAT");
        template.setResultType("TIME");
        template.setRankingStrategy("ASC");
        template.setDefaultMeta("{}");

        templates.saveAndFlush(template);

        List<UUID> ids = new ArrayList<>();

        for (int i = 0; i < 2; i++) {
            var discipline = new EventDisciplineEntity();
            discipline.setId(UUID.randomUUID());
            discipline.setEventId(event.getId());
            discipline.setDisciplineTemplateId(template.getId());
            discipline.setCode("session-" + i);
            discipline.setName("Freestyle " + i);
            discipline.setCompetitionFormat("INDIVIDUAL");
            discipline.setUnitType("HEAT");
            discipline.setResultType("TIME");
            discipline.setRankingStrategy("ASC");
            discipline.setParticipantLimit(40);
            discipline.setEntryFeeAmount(BigDecimal.ZERO);
            discipline.setEntryFeeCurrency("RUB");
            discipline.setStatus("PUBLISHED");
            discipline.setSettingsJson("{}");
            discipline.setCreatedAt(now);
            discipline.setUpdatedAt(now);

            disciplines.saveAndFlush(discipline);
            ids.add(discipline.getId());

            var registration = new RegistrationEntity();
            registration.setId(UUID.randomUUID());
            registration.setEventId(event.getId());
            registration.setEventDisciplineId(discipline.getId());
            registration.setParticipantUserId(athlete.getUserId());
            registration.setParticipantProfileId(savedProfile.getId());
            registration.setStatus("CONFIRMED");
            registration.setParticipantSnapshot("{}");
            registration.setRegistrationMeta(
                    "{\"seedTimeSeconds\":30}"
            );
            registration.setSubmittedAt(now);
            registration.setCreatedAt(now);
            registration.setUpdatedAt(now);

            new TransactionTemplate(transactions)
                    .executeWithoutResult(transaction -> {
                        entityManager.persist(registration);
                        entityManager.flush();
                    });
        }

        ObjectNode request = (ObjectNode) json.readTree("""
                {
                  "sportId": "10000000-0000-0000-0000-000000000001",
                  "code": "placeholder",
                  "name": "Session rules",
                  "competitionFormat": "INDIVIDUAL",
                  "unitType": "HEAT",
                  "blocks": [
                    {
                      "orderIndex": 0,
                      "executorCode": "ELIGIBILITY_CONFIRMED_ONLY",
                      "config": {}
                    },
                    {
                      "orderIndex": 1,
                      "executorCode": "SEED_BY_METRIC_ASC_NULLS_LAST",
                      "config": {
                        "metricKey": "seedTimeSeconds"
                      }
                    },
                    {
                      "orderIndex": 2,
                      "executorCode": "GROUP_BY_CAPACITY",
                      "config": {
                        "capacity": 2,
                        "unitLabelPrefix": "Heat",
                        "heatOrder": "SLOWEST_FIRST",
                        "partialHeat": "FIRST",
                        "ntPlacement": "WEAKEST"
                      }
                    },
                    {
                      "orderIndex": 3,
                      "executorCode": "ASSIGN_CENTER_OUT",
                      "config": {
                        "allowedPositions": [0, 1],
                        "positionOrder": [0, 1]
                      }
                    }
                  ]
                }
                """);

        request.put("code", "session-" + UUID.randomUUID());

        var createdResponse = mvc.perform(
                        post("/api/v1/protocol-rule-sets")
                                .with(user(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsBytes(request))
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        var created = json.readTree(createdResponse);

        UUID version = UUID.fromString(
                created.path("ruleSetVersionId").asText()
        );

        mvc.perform(
                        post(
                                "/api/v1/protocol-rule-sets/{id}/versions/{version}/publish",
                                created.path("id").asText(),
                                version
                        )
                                .with(user(admin))
                )
                .andExpect(status().isOk());

        return new Fixture(
                event.getId(),
                ids.get(0),
                ids.get(1),
                version,
                UUID.randomUUID(),
                admin
        );
    }

    private GenerateSessionProtocolCommand command(
            Fixture f,
            boolean regenerate,
            long seconds
    ) {
        return new GenerateSessionProtocolCommand(
                f.event(),
                regenerate,
                new TimingPolicy(
                        Duration.ofSeconds(600),
                        Duration.ofSeconds(30),
                        Origin.EXPERIMENTAL,
                        "Integration test only"
                ),
                List.of(
                        new Session(
                                f.session(),
                                new Window(T, T.plusSeconds(seconds)),
                                List.of()
                        )
                ),
                List.of(
                        new GenerateSessionProtocolCommand.Discipline(
                                f.first(),
                                f.version(),
                                f.session(),
                                Duration.ofSeconds(120),
                                "Explicit test duration"
                        ),
                        new GenerateSessionProtocolCommand.Discipline(
                                f.second(),
                                f.version(),
                                f.session(),
                                Duration.ofSeconds(120),
                                "Explicit test duration"
                        )
                )
        );
    }

    private SessionProtocolGenerationResult run(
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
            return useCase.execute(command);
        } finally {
            SecurityContextHolder.clearContext();
        }
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
                        f.first(),
                        f.second()
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
                        f.first(),
                        f.second()
                ),

                "results",
                jdbc.queryForList(
                        """
                        SELECT r.*
                        FROM results r
                        JOIN competition_unit_entries e
                          ON e.id = r.competition_unit_entry_id
                        JOIN competition_units u
                          ON u.id = e.competition_unit_id
                        WHERE u.event_discipline_id IN (?, ?)
                        ORDER BY r.id
                        """,
                        f.first(),
                        f.second()
                ),

                "disciplineSnapshots",
                jdbc.queryForList(
                        """
                        SELECT *
                        FROM start_protocol_snapshots
                        WHERE event_discipline_id IN (?, ?)
                        ORDER BY id
                        """,
                        f.first(),
                        f.second()
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

    private ObjectNode httpRequest(
            Fixture f,
            boolean regenerate,
            long windowSeconds
    ) {
        var request = json.createObjectNode();
        request.put("regenerate", regenerate);

        var timing = request.putObject("timingPolicy");
        timing.put("minimumRestSeconds", 600);
        timing.put("changeoverSeconds", 30);
        timing.put(
                "sourceDescription",
                "Organizer-confirmed integration test policy"
        );

        var session = request.putArray("sessions").addObject();
        session.put("id", f.session().toString());
        session.put("start", T.toString());
        session.put("end", T.plusSeconds(windowSeconds).toString());
        session.putArray("breaks");

        var requestedDisciplines = request.putArray("disciplines");

        for (UUID disciplineId : List.of(f.first(), f.second())) {
            var discipline = requestedDisciplines.addObject();
            discipline.put("disciplineId", disciplineId.toString());
            discipline.put("ruleSetVersionId", f.version().toString());
            discipline.put("sessionId", f.session().toString());
            discipline.put("heatDurationSeconds", 120);
            discipline.put(
                    "durationBasis",
                    "Explicit integration test planning duration"
            );
        }

        return request;
    }

    private JsonNode generateOverHttp(
            Fixture f,
            boolean regenerate,
            long windowSeconds
    ) throws Exception {
        var response = mvc.perform(
                        post(
                                "/api/v1/events/{eventId}/session-protocol/generate",
                                f.event()
                        )
                                .with(user(f.admin()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsBytes(
                                        httpRequest(
                                                f,
                                                regenerate,
                                                windowSeconds
                                        )
                                ))
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return json.readTree(response);
    }

    @Test
    void savesBothDisciplinesAndReadsScheduledTimesFromDatabase()
            throws Exception {
        var f = fixture();
        var result = run(f, command(f, false, 3600));

        assertEquals(1, result.revisionNo());
        assertEquals(2, result.disciplines().size());
        assertEquals(2, result.schedule().size());

        assertEquals(
                T,
                protocolAccess.findStartProtocol(f.first())
                        .orElseThrow()
                        .units().get(0)
                        .scheduledAt().toInstant()
        );

        assertEquals(
                T.plusSeconds(720),
                protocolAccess.findStartProtocol(f.second())
                        .orElseThrow()
                        .units().get(0)
                        .scheduledAt().toInstant()
        );

        for (var heat : result.schedule()) {
            UUID discipline = jdbc.queryForObject(
                    """
                    SELECT event_discipline_id
                    FROM competition_units
                    WHERE id = ?
                    """,
                    UUID.class,
                    heat.savedHeatId()
            );

            assertEquals(heat.disciplineId(), discipline);
        }

        var input = json.readTree(jdbc.queryForObject(
                """
                SELECT input_json::text
                FROM session_protocol_snapshots
                WHERE id = ?
                """,
                String.class,
                result.snapshotId()
        ));

        assertEquals(
                2,
                input.path("schedulerInput").path("heats").size()
        );
        assertEquals(2, input.path("disciplines").size());

        String profile = input.path("disciplines")
                .get(0)
                .path("confirmedRegistrations")
                .get(0)
                .path("participantProfileId")
                .asText();

        assertNotNull(UUID.fromString(profile));

        assertEquals(
                profile,
                input.path("disciplines")
                        .get(1)
                        .path("confirmedRegistrations")
                        .get(0)
                        .path("participantProfileId")
                        .asText()
        );

        assertEquals(
                profile,
                input.path("schedulerInput")
                        .path("heats")
                        .get(0)
                        .path("athleteIds")
                        .get(0)
                        .asText()
        );

        assertEquals(
                2L,
                jdbc.queryForObject(
                        """
                        SELECT count(*)
                        FROM start_protocol_snapshots
                        WHERE event_discipline_id IN (?, ?)
                        """,
                        Long.class,
                        f.first(),
                        f.second()
                ).longValue()
        );
    }

    @Test
    void regenerationAddsRevisionAndPreservesHistoricalSessionSnapshot()
            throws Exception {
        var f = fixture();
        var first = run(f, command(f, false, 3600));

        String before = jdbc.queryForObject(
                """
                SELECT protocol_json::text
                FROM session_protocol_snapshots
                WHERE id = ?
                """,
                String.class,
                first.snapshotId()
        );

        var second = run(f, command(f, true, 3600));

        assertEquals(2, second.revisionNo());

        assertEquals(
                before,
                jdbc.queryForObject(
                        """
                        SELECT protocol_json::text
                        FROM session_protocol_snapshots
                        WHERE id = ?
                        """,
                        String.class,
                        first.snapshotId()
                )
        );

        assertNotEquals(
                first.schedule().get(0).savedHeatId(),
                second.schedule().get(0).savedHeatId()
        );

        assertEquals(
                4L,
                jdbc.queryForObject(
                        """
                        SELECT count(*)
                        FROM start_protocol_snapshots
                        WHERE event_discipline_id IN (?, ?)
                        """,
                        Long.class,
                        f.first(),
                        f.second()
                ).longValue()
        );
    }

    @Test
    void insufficientWindowBeforeFirstGenerationWritesNothing()
            throws Exception {
        var f = fixture();
        var before = state(f);

        assertThrows(
                BusinessException.class,
                () -> run(f, command(f, false, 300))
        );

        assertEquals(before, state(f));
    }

    @Test
    void insufficientWindowDuringRegenerationPreservesAllPreviousData()
            throws Exception {
        var f = fixture();
        run(f, command(f, false, 3600));

        var before = state(f);

        assertThrows(
                BusinessException.class,
                () -> run(f, command(f, true, 300))
        );

        assertEquals(before, state(f));
    }

    @Test
    void failureOnSecondDisciplineRollsBackReplacementOfFirst()
            throws Exception {
        var f = fixture();
        run(f, command(f, false, 3600));

        jdbc.update(
                """
                UPDATE competition_units
                SET status = 'PUBLISHED',
                    version = version + 1
                WHERE event_discipline_id = ?
                """,
                f.second()
        );

        var before = state(f);

        assertThrows(
                BusinessException.class,
                () -> run(f, command(f, true, 3600))
        );

        assertEquals(before, state(f));
    }

    @Test
    void finalSessionSnapshotFailureRollsBackUnitsTimesAndBothKindsOfSnapshot()
            throws Exception {
        var f = fixture();
        run(f, command(f, false, 3600));

        var before = state(f);

        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "");

        String function = "session_fail_" + suffix;
        String trigger = "session_trigger_" + suffix;

        jdbc.execute("""
                CREATE FUNCTION %s() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                  IF NEW.event_id = '%s'::uuid
                     AND NEW.revision_no = 2 THEN
                    RAISE EXCEPTION 'injected session snapshot failure';
                  END IF;
                  RETURN NEW;
                END;
                $$
                """.formatted(function, f.event()));

        try {
            jdbc.execute(
                    "CREATE TRIGGER " + trigger
                    + " BEFORE INSERT ON session_protocol_snapshots"
                    + " FOR EACH ROW EXECUTE FUNCTION "
                    + function + "()"
            );

            assertThrows(
                    DataAccessException.class,
                    () -> run(f, command(f, true, 3600))
            );

            assertEquals(before, state(f));
        } finally {
            jdbc.execute(
                    "DROP TRIGGER IF EXISTS " + trigger
                    + " ON session_protocol_snapshots"
            );
            jdbc.execute(
                    "DROP FUNCTION IF EXISTS " + function + "()"
            );
        }
    }

    @Test
    void rejectsWrongEventWithoutWrites() throws Exception {
        var f = fixture();
        var original = command(f, false, 3600);

        var bad = new GenerateSessionProtocolCommand(
                UUID.randomUUID(),
                false,
                original.timingPolicy(),
                original.sessions(),
                original.disciplines()
        );

        var before = state(f);

        assertThrows(
                BusinessException.class,
                () -> run(f, bad)
        );

        assertEquals(before, state(f));
    }

    @Test
    void existingProtocolRequiresExplicitRegeneration() throws Exception {
        var f = fixture();
        run(f, command(f, false, 3600));

        var before = state(f);

        assertThrows(
                BusinessException.class,
                () -> run(f, command(f, false, 3600))
        );

        assertEquals(before, state(f));
    }

    @Test
    void unauthenticatedInvocationIsRejectedBeforeWrites()
            throws Exception {
        var f = fixture();
        var before = state(f);

        SecurityContextHolder.clearContext();

        assertThrows(
                AccessDeniedException.class,
                () -> useCase.execute(command(f, false, 3600))
        );

        assertEquals(before, state(f));
    }

    @Test
    void participantRoleCannotInvokeBatchGeneration() throws Exception {
        var f = fixture();
        var participant = actor("PARTICIPANT");
        var before = state(f);

        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        participant,
                        "",
                        participant.getAuthorities()
                )
        );

        SecurityContextHolder.setContext(context);

        try {
            assertThrows(
                    AccessDeniedException.class,
                    () -> useCase.execute(command(f, false, 3600))
            );
        } finally {
            SecurityContextHolder.clearContext();
        }

        assertEquals(before, state(f));
    }

    @Test
    void httpGenerationReturnsPersistedSchedule() throws Exception {
        var f = fixture();

        JsonNode response = generateOverHttp(f, false, 3600);

        assertEquals(
                f.event().toString(),
                response.path("eventId").asText()
        );
        assertEquals(1, response.path("revisionNo").asInt());
        assertEquals(2, response.path("disciplines").size());
        assertEquals(2, response.path("schedule").size());

        UUID snapshotId = UUID.fromString(
                response.path("snapshotId").asText()
        );

        JsonNode persisted = json.readTree(jdbc.queryForObject(
                """
                SELECT protocol_json::text
                FROM session_protocol_snapshots
                WHERE id = ?
                """,
                String.class,
                snapshotId
        ));

        assertEquals(persisted, response);

        Set<UUID> actualDisciplines = new HashSet<>();

        for (JsonNode heat : response.path("schedule")) {
            UUID savedHeatId = UUID.fromString(
                    heat.path("savedHeatId").asText()
            );
            UUID disciplineId = UUID.fromString(
                    heat.path("disciplineId").asText()
            );

            actualDisciplines.add(disciplineId);

            UUID savedDisciplineId = jdbc.queryForObject(
                    """
                    SELECT event_discipline_id
                    FROM competition_units
                    WHERE id = ?
                    """,
                    UUID.class,
                    savedHeatId
            );

            OffsetDateTime savedStart = jdbc.queryForObject(
                    """
                    SELECT scheduled_at
                    FROM competition_units
                    WHERE id = ?
                    """,
                    (rs, rowNum) ->
                            rs.getObject(
                                    "scheduled_at",
                                    OffsetDateTime.class
                            ),
                    savedHeatId
            );

            assertEquals(disciplineId, savedDisciplineId);
            assertNotNull(savedStart);

            assertEquals(
                    Instant.parse(heat.path("start").asText()),
                    savedStart.toInstant()
            );
        }

        assertEquals(
                Set.of(f.first(), f.second()),
                actualDisciplines
        );

        assertEquals(
                T,
                protocolAccess.findStartProtocol(f.first())
                        .orElseThrow()
                        .units().get(0)
                        .scheduledAt().toInstant()
        );

        assertEquals(
                T.plusSeconds(720),
                protocolAccess.findStartProtocol(f.second())
                        .orElseThrow()
                        .units().get(0)
                        .scheduledAt().toInstant()
        );
    }

    @Test
    void httpParticipantCannotGenerateAndWritesNothing()
            throws Exception {
        var f = fixture();
        var participant = actor("PARTICIPANT");
        var before = state(f);

        mvc.perform(
                        post(
                                "/api/v1/events/{eventId}/session-protocol/generate",
                                f.event()
                        )
                                .with(user(participant))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsBytes(
                                        httpRequest(f, false, 3600)
                                ))
                )
                .andExpect(status().isForbidden());

        assertEquals(before, state(f));
    }

    @Test
    void httpUnrelatedOrganizerCannotGenerateAndWritesNothing()
            throws Exception {
        var f = fixture();
        var outsider = actor("ORGANIZER");
        var before = state(f);

        mvc.perform(
                        post(
                                "/api/v1/events/{eventId}/session-protocol/generate",
                                f.event()
                        )
                                .with(user(outsider))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsBytes(
                                        httpRequest(f, false, 3600)
                                ))
                )
                .andExpect(status().isForbidden());

        assertEquals(before, state(f));
    }

    @Test
    void httpImpossibleRegenerationPreservesPreviousData()
            throws Exception {
        var f = fixture();

        generateOverHttp(f, false, 3600);

        var before = state(f);

        var response = mvc.perform(
                        post(
                                "/api/v1/events/{eventId}/session-protocol/generate",
                                f.event()
                        )
                                .with(user(f.admin()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsBytes(
                                        httpRequest(f, true, 300)
                                ))
                )
                .andExpect(status().isBadRequest())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertEquals(
                "protocol.session_window_exceeded",
                json.readTree(response).path("code").asText()
        );

        assertEquals(before, state(f));
    }
}