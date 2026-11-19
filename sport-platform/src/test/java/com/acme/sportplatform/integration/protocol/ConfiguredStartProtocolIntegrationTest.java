package com.acme.sportplatform.integration.protocol;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.sportplatform.competition.infrastructure.jpa.EventDisciplineEntity;
import com.acme.sportplatform.competition.infrastructure.jpa.EventDisciplineRepository;
import com.acme.sportplatform.events.infrastructure.persistence.entity.EventEntity;
import com.acme.sportplatform.events.infrastructure.persistence.repository.EventRepository;
import com.acme.sportplatform.identity.infrastructure.persistence.entity.ProfileEntity;
import com.acme.sportplatform.identity.infrastructure.persistence.entity.UserEntity;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.ProfileRepository;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.UserRepository;
import com.acme.sportplatform.identity.infrastructure.security.PlatformUserPrincipal;
import com.acme.sportplatform.protocol.api.GenerateStartProtocolRequest;
import com.acme.sportplatform.protocol.application.GenerateStartProtocolUseCase;
import com.acme.sportplatform.protocol.infrastructure.jpa.StartProtocolSnapshotRepository;
import com.acme.sportplatform.results.infrastructure.jpa.CompetitionUnitRepository;
import com.acme.sportplatform.sports.infrastructure.jpa.DisciplineTemplateEntity;
import com.acme.sportplatform.sports.infrastructure.jpa.DisciplineTemplateRepository;
import com.acme.sportplatform.support.AbstractPostgresIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@AutoConfigureMockMvc
class ConfiguredStartProtocolIntegrationTest extends AbstractPostgresIntegrationTest {
    private static final UUID DEMO_EVENT_ID = UUID.randomUUID();

    @org.springframework.test.context.DynamicPropertySource
    static void demoProperties(
            org.springframework.test.context.DynamicPropertyRegistry registry
    ) {
        registry.add("app.demo-data.enabled", () -> "true");
        registry.add("app.demo-data.allowed-event-ids", DEMO_EVENT_ID::toString);
    }

    private static final UUID SPORT = UUID.fromString("10000000-0000-0000-0000-000000000001");
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository users;
    @Autowired private ProfileRepository profiles;
    @Autowired private EventRepository events;
    @Autowired private DisciplineTemplateRepository templates;
    @Autowired private EventDisciplineRepository disciplines;
    @Autowired private CompetitionUnitRepository units;
    @Autowired private StartProtocolSnapshotRepository snapshots;
    @Autowired private GenerateStartProtocolUseCase generateUseCase;
    @Autowired private JdbcTemplate jdbc;

    private PlatformUserPrincipal actor(String role) {
        UserEntity entity = new UserEntity();
        entity.setEmail("configured-" + UUID.randomUUID() + "@example.com");
        entity.setPasswordHash("test-password-hash"); entity.setStatus("active");
        entity = users.saveAndFlush(entity);
        return new PlatformUserPrincipal(entity.getId(), entity.getEmail(), "", "active",
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
    private Fixture fixture() throws Exception {
        return fixture(false);
    }

    private Fixture fixture(boolean demo) throws Exception {
        PlatformUserPrincipal admin = actor("PLATFORM_ADMIN");
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        
        EventEntity event = new EventEntity();
        event.setId(demo ? DEMO_EVENT_ID : UUID.randomUUID()); event.setCreatedByUserId(admin.getUserId());
        event.setOrganizationId(UUID.randomUUID()); event.setVenueId(UUID.randomUUID());
        event.setSportId(SPORT); event.setRegulationVersionId(UUID.randomUUID());
        event.setTitle("Configured protocol test"); event.setDescription("Fixture");
        event.setRegistrationOpenAt(now.minusDays(1)); event.setRegistrationCloseAt(now.plusDays(10));
        event.setEventStartAt(now.plusDays(20)); event.setEventEndAt(now.plusDays(21));
        event.setStatus(demo ? "DRAFT" : "REGISTRATION_OPEN"); event.setPublicSlug("configured-" + UUID.randomUUID());
        event.setSettingsJson("{}"); event.setCreatedAt(now); event.setUpdatedAt(now);
        events.saveAndFlush(event);

        DisciplineTemplateEntity template = new DisciplineTemplateEntity();
        template.setId(UUID.randomUUID()); template.setSportId(SPORT);
        template.setCode("configured-" + UUID.randomUUID()); template.setName("50m Freestyle");
        template.setCompetitionFormat("INDIVIDUAL"); template.setUnitType("HEAT");
        template.setResultType("TIME"); template.setRankingStrategy("ASC"); template.setDefaultMeta("{}");
        templates.saveAndFlush(template);

        EventDisciplineEntity discipline = new EventDisciplineEntity();
        discipline.setId(UUID.randomUUID()); discipline.setEventId(event.getId());
        discipline.setDisciplineTemplateId(template.getId()); discipline.setCode("50m");
        discipline.setName("50m Freestyle"); discipline.setCompetitionFormat("INDIVIDUAL");
        discipline.setUnitType("HEAT"); discipline.setResultType("TIME"); discipline.setRankingStrategy("ASC");
        discipline.setParticipantLimit(40); discipline.setEntryFeeAmount(BigDecimal.ZERO);
        discipline.setEntryFeeCurrency("RUB"); discipline.setStatus(demo ? "DRAFT" : "PUBLISHED");
        discipline.setSettingsJson(demo ? "{\"gender\":\"MALE\"}" : "{}"); discipline.setCreatedAt(now); discipline.setUpdatedAt(now);
        disciplines.saveAndFlush(discipline);
        if (demo) {
            return new Fixture(discipline.getId(), admin);
        }

        PlatformUserPrincipal participant = actor("PARTICIPANT");
        ProfileEntity profile = new ProfileEntity(); profile.setUserId(participant.getUserId());
        profile.setFirstName("Ivan"); profile.setLastName("Petrov");
        profile.setBirthDate(LocalDate.parse("2004-05-10")); profile.setGender("MALE");
        profile.setCountryCode("RU"); profile.setCity("Moscow"); profile.setClubName("Test");
        profile.setSportMeta(Map.of("sportRank", "FIRST_CATEGORY")); profiles.saveAndFlush(profile);

        String response = mvc.perform(post("/api/v1/registrations").with(user(participant))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(Map.of("eventDisciplineId", discipline.getId(),
                                "registrationMeta", Map.of("seedTimeSeconds", 28.11,
                                        "emergencyContactPhone", "+79990000000")))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String registrationId = json.readTree(response).path("id").asText();
        mvc.perform(post("/api/v1/registrations/{id}/review", registrationId).with(user(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"decision":"CONFIRMED","reviewNote":"Configured protocol fixture"}
                                """))
                .andExpect(status().isOk());
        return new Fixture(discipline.getId(), admin);
    }
    private UUID rule(Fixture f, boolean publish) throws Exception {
        ObjectNode request = (ObjectNode) json.readTree("""
                {
                  "sportId":"10000000-0000-0000-0000-000000000001",
                  "code":"placeholder","name":"Configured rules",
                  "competitionFormat":"INDIVIDUAL","unitType":"HEAT",
                  "blocks":[
                    {"orderIndex":0,"executorCode":"ELIGIBILITY_CONFIRMED_ONLY","config":{}},
                    {"orderIndex":1,"executorCode":"SEED_BY_METRIC_ASC_NULLS_LAST",
                     "config":{"metricKey":"seedTimeSeconds"}},
                    {"orderIndex":2,"executorCode":"GROUP_BY_CAPACITY",
                     "config":{"capacity":2,"unitLabelPrefix":"Configured","heatOrder":"SLOWEST_FIRST",
                               "partialHeat":"FIRST","ntPlacement":"WEAKEST"}},
                    {"orderIndex":3,"executorCode":"ASSIGN_CENTER_OUT",
                     "config":{"allowedPositions":[0,1],"positionOrder":[0,1]}}
                  ]
                }
                """);
        request.put("code", "configured-" + UUID.randomUUID());
        JsonNode created = json.readTree(mvc.perform(post("/api/v1/protocol-rule-sets")
                        .with(user(f.admin())).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(request)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        UUID versionId = UUID.fromString(created.path("ruleSetVersionId").asText());
        if (publish) mvc.perform(post("/api/v1/protocol-rule-sets/{id}/versions/{version}/publish",
                        created.path("id").asText(), versionId).with(user(f.admin())))
                .andExpect(status().isOk());
        return versionId;
    }
    private ResultActions generate(Fixture f, UUID version, PlatformUserPrincipal actor) throws Exception {
        return mvc.perform(post("/api/v1/event-disciplines/{id}/start-protocol/generate", f.discipline())
                .with(user(actor)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("regenerate", false, "ruleSetVersionId", version))));
    }
    private void noWrites(Fixture f) {
        assertTrue(units.findByEventDisciplineIdOrderBySequenceNumberAsc(f.discipline()).isEmpty());
        assertEquals(0, snapshots.countByEventDisciplineId(f.discipline()));
    }

    @Test 
    void shouldGenerateFromPublishedRulesAndSaveSnapshot() throws Exception {
        Fixture f = fixture(); 
        UUID version = rule(f, true);
        generate(f, version, f.admin()).andExpect(status().isOk())
                .andExpect(jsonPath("$.assignmentCount").value(1))
                .andExpect(jsonPath("$.units[0].label").value("Configured 1"))
                .andExpect(jsonPath("$.units[0].assignments[0].laneOrPosition").value(0));
        var snapshot = snapshots.findFirstByEventDisciplineIdOrderByRevisionNoDesc(f.discipline()).orElseThrow();
        assertEquals(version, snapshot.getRuleSetVersionId());
        assertEquals(f.admin().getUserId(), snapshot.getCreatedByUserId());
        assertEquals(Integer.valueOf(1), snapshot.getRevisionNo());
        assertEquals("GENERATE", snapshot.getOperation());
        assertEquals(1, snapshot.getInput().path("participants").size());
        assertEquals(0, snapshot.getProtocol().path("units").get(0).path("assignments").get(0)
                .path("laneOrPosition").asInt(-1));
    }
    @Test 
    void shouldRejectDraftVersionWithoutWritingUnits() throws Exception {
        Fixture f = fixture(); 
        UUID version = rule(f, false);
        generate(f, version, f.admin()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("protocol.rule_version_not_published"));
        noWrites(f);
    }
    @Test 
    void shouldRejectIncompatibleUnitType() throws Exception {
        Fixture f = fixture(); 
        UUID version = rule(f, true);
        
        var discipline = disciplines.findById(f.discipline()).orElseThrow();
        discipline.setUnitType("MATCH"); 
        disciplines.saveAndFlush(discipline);
        generate(f, version, f.admin()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("protocol.rule_version_incompatible"));
        noWrites(f);
    }
    @Test 
    void shouldNotFallbackWhenExplicitVersionIsUnknown() throws Exception {
        Fixture f = fixture();
        generate(f, UUID.randomUUID(), f.admin()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("protocol.rule_version_not_found"));
        noWrites(f);
    }
    @Test 
    void shouldDenyOtherOrganizerUseOfRules() throws Exception {
        Fixture f = fixture(); 
        UUID version = rule(f, true);
        generate(f, version, actor("ORGANIZER")).andExpect(status().isForbidden());
        noWrites(f);
    }
        @Test
        void shouldRejectRepeatedGenerationWithoutCreatingAnotherSnapshot()
                throws Exception {

            Fixture f = fixture();
            UUID version = rule(f, true);

            generate(f, version, f.admin())
                    .andExpect(status().isOk());

            generate(f, version, f.admin())
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code")
                            .value("protocol.already_generated"));

            assertEquals(
                    1,
                    snapshots.countByEventDisciplineId(f.discipline())
            );

            assertEquals(
                    1,
                    units.findByEventDisciplineIdOrderBySequenceNumberAsc(
                            f.discipline()
                    ).size()
            );
        }
    @Test 
    void shouldRollbackCreatedUnitsIfSnapshotInsertFails() throws Exception {
        Fixture f = fixture(); 
        UUID version = rule(f, true);
        String suffix = UUID.randomUUID().toString().replace("-", "");
        
        String function = "snapshot_fail_" + suffix, trigger = "snapshot_trigger_" + suffix;
        
        jdbc.execute("""
                CREATE FUNCTION %s() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    IF NEW.event_discipline_id = '%s'::uuid THEN
                        RAISE EXCEPTION 'injected snapshot insert failure';
                    END IF;
                    RETURN NEW;
                END;
                $$
                """.formatted(function, f.discipline()));
        try {
            jdbc.execute("CREATE TRIGGER " + trigger
                    + " BEFORE INSERT ON start_protocol_snapshots FOR EACH ROW EXECUTE FUNCTION "
                    + function + "()");
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new UsernamePasswordAuthenticationToken(f.admin(), "",
                    f.admin().getAuthorities()));
            SecurityContextHolder.setContext(context);
            try {
                assertThrows(DataAccessException.class, () -> generateUseCase.execute(
                        f.discipline(), new GenerateStartProtocolRequest(false, version)));
            } finally { SecurityContextHolder.clearContext(); }
            noWrites(f);
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS " + trigger + " ON start_protocol_snapshots");
            jdbc.execute("DROP FUNCTION IF EXISTS " + function + "()");
        }
    }
    private JsonNode createMaleDemoBatch(
            UUID eventId, UUID disciplineId, UUID boundVersion,
            PlatformUserPrincipal admin
    ) throws Exception {
        String path = "/api/v1/events/{eventId}/disciplines/{disciplineId}/demo-registrations";

        JsonNode plan = json.readTree(mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .get(path, eventId, disciplineId).with(user(admin)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertEquals("AVAILABLE", plan.path("state").asText(), plan.toString());
        assertEquals(boundVersion.toString(), plan.path("ruleVersionId").asText());

        JsonNode planned = plan.path("participants");
        assertTrue(planned.isArray());
        assertTrue(planned.size() > 0);
        assertEquals(plan.path("count").asInt(), planned.size());

        ObjectNode request = json.createObjectNode();
        request.put("requestId", UUID.randomUUID().toString());
        request.set("expectedRuleVersionId", plan.path("ruleVersionId"));
        request.set("expectedCapacity", plan.path("capacity"));
        request.set("expectedCount", plan.path("count"));
        request.set("datasetVersion", plan.path("datasetVersion"));
        request.put("confirmed", true);

        JsonNode receipt = json.readTree(mvc.perform(post(path, eventId, disciplineId)
                        .with(user(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        for (String field : List.of("ruleVersionId", "capacity", "count", "datasetVersion")) {
            assertEquals(plan.path(field), receipt.path(field), field);
        }

        JsonNode created = receipt.path("participants");
        assertTrue(created.isArray());
        assertEquals(planned.size(), created.size());

        var ids = new java.util.HashSet<UUID>();
        int withoutTime = 0;
        for (int i = 0; i < planned.size(); i++) {
            JsonNode expected = planned.get(i);
            JsonNode actual = created.get(i);
            for (String field : List.of(
                    "ordinal", "name", "gender", "birthDate", "seedTimeSeconds",
                    "categoryCode", "seedingGroupCode")) {
                assertEquals(expected.path(field), actual.path(field), field + ", row " + i);
            }
            assertEquals("MALE", actual.path("gender").asText());
            java.time.LocalDate.parse(actual.path("birthDate").asText());

            JsonNode time = actual.path("seedTimeSeconds");
            if (time.isNull() || time.isMissingNode()) {
                withoutTime++;
            }
            assertTrue(ids.add(UUID.fromString(actual.path("registrationId").asText())),
                    "Duplicate registrationId at row " + i);
        }
        assertEquals(1, withoutTime);
        return receipt;
    }

    @Test
    void maleDemoBatchSurvivesPersistenceAndProtocolGeneration() throws Exception {
        Fixture f = fixture(true);
        UUID boundVersion = bindDemoRegulation(f);

        JsonNode receipt = createMaleDemoBatch(
                DEMO_EVENT_ID, f.discipline(), boundVersion, f.admin());
        var expectedIds = new java.util.ArrayList<UUID>();

        for (JsonNode row : receipt.path("participants")) {
            UUID id = UUID.fromString(row.path("registrationId").asText());
            expectedIds.add(id);

            var stored = jdbc.queryForMap("""
                    SELECT status, participant_snapshot::text AS snapshot,
                           registration_meta::text AS meta
                    FROM registrations
                    WHERE id = ? AND event_id = ? AND event_discipline_id = ?
                    """, id, DEMO_EVENT_ID, f.discipline());

            assertEquals("CONFIRMED", stored.get("status"), id.toString());
            JsonNode snapshot = json.readTree((String) stored.get("snapshot"));
            JsonNode meta = json.readTree((String) stored.get("meta"));
            assertEquals(row.path("gender"), snapshot.path("gender"), id.toString());
            assertEquals(row.path("birthDate"), snapshot.path("birthDate"), id.toString());
            assertTrue(meta.path("demoFixture").asBoolean(), id.toString());
            assertEquals(receipt.path("requestId"), meta.path("demoBatchId"), id.toString());

            JsonNode expectedTime = row.path("seedTimeSeconds");
            if (expectedTime.isNull() || expectedTime.isMissingNode()) {
                assertTrue(!meta.has("seedTimeSeconds"), id.toString());
            } else {
                JsonNode storedTime = meta.path("seedTimeSeconds");
                assertTrue(storedTime.isNumber(), id.toString());
                assertEquals(0, expectedTime.decimalValue()
                        .compareTo(storedTime.decimalValue()), id.toString());
            }
        }

        var persistedIds = jdbc.queryForList("""
                SELECT id FROM registrations
                WHERE event_id = ? AND event_discipline_id = ?
                """, UUID.class, DEMO_EVENT_ID, f.discipline());
        var sortedExpectedIds = expectedIds.stream().sorted().toList();
        assertEquals(sortedExpectedIds,
                persistedIds.stream().sorted().toList(), "Persisted registrations");

        var discipline = disciplines.findById(f.discipline()).orElseThrow();
        discipline.setStatus("PUBLISHED");
        disciplines.saveAndFlush(discipline);

        JsonNode protocol = json.readTree(generate(f, boundVersion, f.admin())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignmentCount").value(expectedIds.size()))
                .andReturn().getResponse().getContentAsString());

        var assignedIds = new java.util.ArrayList<UUID>();
        for (JsonNode unit : protocol.path("units")) {
            for (JsonNode assignment : unit.path("assignments")) {
                assignedIds.add(UUID.fromString(assignment.path("registrationId").asText()));
            }
        }
        assertEquals(sortedExpectedIds,
                assignedIds.stream().sorted().toList(), "Protocol assignments");

        var snapshot = snapshots.findFirstByEventDisciplineIdOrderByRevisionNoDesc(
                f.discipline()).orElseThrow();
        assertEquals(boundVersion, snapshot.getRuleSetVersionId());
    }

    private UUID bindDemoRegulation(Fixture f) throws Exception {
        ObjectNode request = json.createObjectNode();
        request.putNull("sourceVersionId");
        request.put("sportId", SPORT.toString());
        request.put("code", "NF02-DEMO-" + UUID.randomUUID());
        request.put("name", "NF-02 demo protocol integration");
        request.putNull("description");
        request.put("versionNo", 1);
        request.putNull("effectiveFrom");
        request.putNull("notes");
        request.putArray("conditions").add("NF-02 integration fixture");

        ObjectNode protocol = request.putObject("protocol");
        protocol.put("capacity", 4);
        protocol.set("positionOrder", json.valueToTree(List.of(1, 2, 3, 4)));
        protocol.put("metricKey", "seedTimeSeconds");
        protocol.put("heatOrder", "SLOWEST_FIRST");
        protocol.put("partialHeat", "FIRST");

        JsonNode document = json.readTree(mvc.perform(
                        post("/api/v1/regulation-configurations")
                                .with(user(f.admin()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsBytes(request)))
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString()).path("document");

        UUID regulationId = UUID.fromString(document.path("id").asText());
        UUID ruleVersionId = UUID.fromString(
                document.path("binding").path("ruleVersionId").asText());

        assertEquals(1, jdbc.update("""
                UPDATE events SET regulation_version_id = ?
                WHERE id = ? AND status = 'DRAFT'
                """, regulationId, DEMO_EVENT_ID));
        return ruleVersionId;
    }

    private record Fixture(UUID discipline, PlatformUserPrincipal admin) { }
}
