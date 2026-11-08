package com.acme.sportplatform.integration.protocol;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import org.springframework.test.web.servlet.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;
import com.acme.sportplatform.support.AbstractPostgresIntegrationTest;
import com.acme.sportplatform.identity.infrastructure.persistence.entity.*;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.*;
import com.acme.sportplatform.identity.infrastructure.security.PlatformUserPrincipal;
import com.acme.sportplatform.events.infrastructure.persistence.entity.EventEntity;
import com.acme.sportplatform.events.infrastructure.persistence.repository.EventRepository;
import com.acme.sportplatform.competition.infrastructure.jpa.*;
import com.acme.sportplatform.sports.infrastructure.jpa.*;
import com.acme.sportplatform.protocol.infrastructure.jpa.*;
import com.acme.sportplatform.protocol.application.GenerateStartProtocolUseCase;
import com.acme.sportplatform.protocol.api.GenerateStartProtocolRequest;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;

@AutoConfigureMockMvc
class StartProtocolRegenerationIntegrationTest extends AbstractPostgresIntegrationTest {
    private static final UUID SPORT = UUID.fromString("10000000-0000-0000-0000-000000000001");
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository users;
    @Autowired private ProfileRepository profiles;
    @Autowired private EventRepository events;
    @Autowired private DisciplineTemplateRepository templates;
    @Autowired private EventDisciplineRepository disciplines;
    @Autowired private StartProtocolSnapshotRepository snapshots;
    @Autowired private GenerateStartProtocolUseCase useCase;
    @Autowired private JdbcTemplate jdbc;

    private PlatformUserPrincipal actor(String role) {
        UserEntity entity = new UserEntity();
        entity.setEmail("regen-" + UUID.randomUUID() + "@example.com");
        entity.setPasswordHash("test-password-hash"); entity.setStatus("active"); entity = users.saveAndFlush(entity);
        return new PlatformUserPrincipal(entity.getId(), entity.getEmail(), "", "active",
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
    private Fixture fixture() throws Exception {
        var admin = actor("PLATFORM_ADMIN"); OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        var event = new EventEntity(); event.setId(UUID.randomUUID()); event.setCreatedByUserId(admin.getUserId());
        event.setOrganizationId(UUID.randomUUID()); event.setVenueId(UUID.randomUUID()); event.setSportId(SPORT);
        event.setRegulationVersionId(UUID.randomUUID()); event.setTitle("Regeneration test"); event.setDescription("Fixture");
        event.setRegistrationOpenAt(now.minusDays(1)); event.setRegistrationCloseAt(now.plusDays(10));
        event.setEventStartAt(now.plusDays(20)); event.setEventEndAt(now.plusDays(21));
        event.setStatus("REGISTRATION_OPEN"); event.setPublicSlug("regen-" + UUID.randomUUID());
        event.setSettingsJson("{}"); event.setCreatedAt(now); event.setUpdatedAt(now); events.saveAndFlush(event);
        var template = new DisciplineTemplateEntity(); template.setId(UUID.randomUUID()); template.setSportId(SPORT);
        template.setCode("regen-" + UUID.randomUUID()); template.setName("50m Freestyle");
        template.setCompetitionFormat("INDIVIDUAL"); template.setUnitType("HEAT");
        template.setResultType("TIME"); template.setRankingStrategy("ASC"); template.setDefaultMeta("{}");
        templates.saveAndFlush(template);
        var discipline = new EventDisciplineEntity(); discipline.setId(UUID.randomUUID()); discipline.setEventId(event.getId());
        discipline.setDisciplineTemplateId(template.getId()); discipline.setCode("50m"); discipline.setName("50m Freestyle");
        discipline.setCompetitionFormat("INDIVIDUAL"); discipline.setUnitType("HEAT");
        discipline.setResultType("TIME"); discipline.setRankingStrategy("ASC"); discipline.setParticipantLimit(40);
        discipline.setEntryFeeAmount(BigDecimal.ZERO); discipline.setEntryFeeCurrency("RUB"); discipline.setStatus("PUBLISHED");
        discipline.setSettingsJson("{}"); discipline.setCreatedAt(now); discipline.setUpdatedAt(now);
        disciplines.saveAndFlush(discipline);
        UUID first = registration(discipline.getId(), admin, 28.11), second = registration(discipline.getId(), admin, 30.00);
        UUID version = rule(admin);
        return new Fixture(discipline.getId(), first, second, version, admin);
    }
    private UUID registration(UUID discipline, PlatformUserPrincipal admin, double seed) throws Exception {
        var participant = actor("PARTICIPANT"); var profile = new ProfileEntity(); profile.setUserId(participant.getUserId());
        profile.setFirstName("Ivan"); profile.setLastName("Petrov"); profile.setBirthDate(LocalDate.parse("2004-05-10"));
        profile.setGender("MALE"); profile.setCountryCode("RU"); profile.setCity("Moscow"); profile.setClubName("Test");
        profile.setSportMeta(Map.of("sportRank", "FIRST_CATEGORY")); profiles.saveAndFlush(profile);
        String raw = mvc.perform(post("/api/v1/registrations").with(user(participant))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of(
                                "eventDisciplineId", discipline, "registrationMeta", Map.of(
                                        "seedTimeSeconds", seed, "emergencyContactPhone", "+79990000000")))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(json.readTree(raw).path("id").asText());
        mvc.perform(post("/api/v1/registrations/{id}/review", id).with(user(admin))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"decision":"CONFIRMED","reviewNote":"Regeneration fixture"}
                                """))
                .andExpect(status().isOk());
        return id;
    }
    private UUID rule(PlatformUserPrincipal admin) throws Exception {
        ObjectNode request = (ObjectNode) json.readTree("""
                {
                  "sportId":"10000000-0000-0000-0000-000000000001",
                  "code":"placeholder","name":"Regeneration rules","competitionFormat":"INDIVIDUAL","unitType":"HEAT",
                  "blocks":[
                    {"orderIndex":0,"executorCode":"ELIGIBILITY_CONFIRMED_ONLY","config":{}},
                    {"orderIndex":1,"executorCode":"SEED_BY_METRIC_ASC_NULLS_LAST","config":{"metricKey":"seedTimeSeconds"}},
                    {"orderIndex":2,"executorCode":"GROUP_BY_CAPACITY","config":{"capacity":2,"unitLabelPrefix":"Heat",
                     "heatOrder":"SLOWEST_FIRST","partialHeat":"FIRST","ntPlacement":"WEAKEST"}},
                    {"orderIndex":3,"executorCode":"ASSIGN_CENTER_OUT","config":{"allowedPositions":[0,1],"positionOrder":[0,1]}}
                  ]
                }
                """);
        request.put("code", "regen-" + UUID.randomUUID());
        var created = json.readTree(mvc.perform(post("/api/v1/protocol-rule-sets").with(user(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(request)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        UUID version = UUID.fromString(created.path("ruleSetVersionId").asText());
        mvc.perform(post("/api/v1/protocol-rule-sets/{id}/versions/{version}/publish",
                        created.path("id").asText(), version).with(user(admin))).andExpect(status().isOk());
        return version;
    }
    private ResultActions request(Fixture f, boolean regenerate) throws Exception {
        return mvc.perform(post("/api/v1/event-disciplines/{id}/start-protocol/generate", f.discipline())
                .with(user(f.admin())).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("regenerate", regenerate, "ruleSetVersionId", f.version()))));
    }
    private JsonNode generate(Fixture f, boolean regenerate) throws Exception {
        return json.readTree(request(f, regenerate).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }
    private Map<String, List<Map<String, Object>>> state(Fixture f) {
        return Map.of(
                "units", jdbc.queryForList("SELECT * FROM competition_units WHERE event_discipline_id=? ORDER BY id", f.discipline()),
                "entries", jdbc.queryForList("""
                        SELECT e.* FROM competition_unit_entries e JOIN competition_units u ON u.id=e.competition_unit_id
                        WHERE u.event_discipline_id=? ORDER BY e.id
                        """, f.discipline()),
                "results", jdbc.queryForList("""
                        SELECT r.* FROM results r JOIN competition_unit_entries e ON e.id=r.competition_unit_entry_id
                        JOIN competition_units u ON u.id=e.competition_unit_id WHERE u.event_discipline_id=? ORDER BY r.id
                        """, f.discipline()),
                "snapshots", jdbc.queryForList("SELECT * FROM start_protocol_snapshots WHERE event_discipline_id=? ORDER BY revision_no", f.discipline()));
    }
    private List<String> signature(JsonNode protocol) {
        List<String> result = new ArrayList<>();
        for (var unit : protocol.path("units")) for (var a : unit.path("assignments")) {
            result.add(unit.path("sequenceNumber").asInt() + ":" + a.path("registrationId").asText()
                    + ":" + a.path("laneOrPosition").asInt());
        }
        Collections.sort(result); return result;
    }

    @Test void shouldIncludeNewConfirmedRegistrationAndPreservePreviousSnapshot() throws Exception {
        Fixture f = fixture(); generate(f, false);
        UUID oldUnitId = jdbc.queryForObject(
                "SELECT id FROM competition_units WHERE event_discipline_id=?", UUID.class, f.discipline());
        var firstSnapshot = snapshots.findFirstByEventDisciplineIdOrderByRevisionNoDesc(f.discipline()).orElseThrow();
        JsonNode oldProtocol = firstSnapshot.getProtocol().deepCopy();
        UUID added = registration(f.discipline(), f.admin(), 27.0);
        var after = generate(f, true);
        assertEquals(3, after.path("assignmentCount").asInt()); assertEquals(2, after.path("unitCount").asInt());
        assertTrue(signature(after).stream().anyMatch(s -> s.contains(added.toString())));
        assertEquals(oldProtocol, snapshots.findById(firstSnapshot.getId()).orElseThrow().getProtocol());
        var latest = snapshots.findFirstByEventDisciplineIdOrderByRevisionNoDesc(f.discipline()).orElseThrow();
        assertEquals(Integer.valueOf(2), latest.getRevisionNo()); assertEquals("REGENERATE", latest.getOperation());
        assertEquals(2, snapshots.countByEventDisciplineId(f.discipline()));
        assertEquals(0L, jdbc.queryForObject(
                "SELECT count(*) FROM competition_units WHERE id=?", Long.class, oldUnitId).longValue());
    }
    @Test void shouldExcludeRegistrationNoLongerConfirmed() throws Exception {
        Fixture f = fixture(); generate(f, false);
        jdbc.update("UPDATE registrations SET status='REJECTED' WHERE id=?", f.first());
        var after = generate(f, true);
        assertEquals(1, after.path("assignmentCount").asInt());
        assertTrue(signature(after).stream().noneMatch(s -> s.contains(f.first().toString())));
        assertTrue(signature(after).stream().anyMatch(s -> s.contains(f.second().toString())));
    }
    @Test void shouldReseedAfterSeedMetricChange() throws Exception {
        Fixture f = fixture(); var before = generate(f, false);
        jdbc.update("""
                UPDATE registrations SET registration_meta=jsonb_set(registration_meta,
                    '{seedTimeSeconds}', '99.99'::jsonb) WHERE id=?
                """, f.first());
        var after = generate(f, true);
        assertNotEquals(signature(before), signature(after));
        var laneZero = after.path("units").get(0).path("assignments").get(0);
        assertEquals(f.second().toString(), laneZero.path("registrationId").asText());
        assertEquals(0, laneZero.path("laneOrPosition").asInt());
    }
    @Test void shouldKeepAssignmentsWhenInputsDoNotChange() throws Exception {
        Fixture f = fixture(); var first = generate(f, false); var second = generate(f, true);
        assertEquals(signature(first), signature(second));
        assertEquals(2, snapshots.countByEventDisciplineId(f.discipline()));
    }
    @Test void shouldRejectReplacementOfPublishedUnitWithoutWrites() throws Exception {
        Fixture f = fixture(); generate(f, false);
        jdbc.update("UPDATE competition_units SET status='PUBLISHED',version=version+1 WHERE event_discipline_id=?", f.discipline());
        var before = state(f);
        request(f, true).andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.code").value("protocol.regeneration_unit_published"));
        assertEquals(before, state(f));
    }
    @Test void shouldRejectReplacementWhenAnyResultExists() throws Exception {
        Fixture f = fixture(); generate(f, false);
        UUID entry = jdbc.queryForObject("""
                SELECT e.id FROM competition_unit_entries e JOIN competition_units u ON u.id=e.competition_unit_id
                WHERE u.event_discipline_id=? AND e.registration_id=?
                """, UUID.class, f.discipline(), f.first());
        mvc.perform(post("/api/v1/results").with(user(f.admin())).param("resultType", "TIME")
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of(
                                "competitionUnitEntryId", entry, "rawValue", "28.11", "status", "PENDING"))))
                .andExpect(status().isOk());
        var before = state(f);
        request(f, true).andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.code").value("protocol.regeneration_has_results"));
        assertEquals(before, state(f));
    }
    @Test void shouldRollbackDeletionAndReplacementWhenNewSnapshotFails() throws Exception {
        Fixture f = fixture(); generate(f, false); var before = state(f);
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String function = "regen_fail_" + suffix, trigger = "regen_trigger_" + suffix;
        jdbc.execute("""
                CREATE FUNCTION %s() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    IF NEW.event_discipline_id='%s'::uuid AND NEW.operation='REGENERATE' THEN
                        RAISE EXCEPTION 'injected regeneration snapshot failure';
                    END IF;
                    RETURN NEW;
                END;
                $$
                """.formatted(function, f.discipline()));
        try {
            jdbc.execute("CREATE TRIGGER " + trigger
                    + " BEFORE INSERT ON start_protocol_snapshots FOR EACH ROW EXECUTE FUNCTION " + function + "()");
            var context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(new UsernamePasswordAuthenticationToken(f.admin(), "", f.admin().getAuthorities()));
            SecurityContextHolder.setContext(context);
            try {
                assertThrows(DataAccessException.class, () -> useCase.execute(f.discipline(),
                        new GenerateStartProtocolRequest(true, f.version())));
            } finally { SecurityContextHolder.clearContext(); }
            assertEquals(before, state(f));
        } finally {
            jdbc.execute("DROP TRIGGER IF EXISTS " + trigger + " ON start_protocol_snapshots");
            jdbc.execute("DROP FUNCTION IF EXISTS " + function + "()");
        }
    }
    @Test void shouldKeepPreviousProtocolWhenAllRegistrationsBecomeIneligible() throws Exception {
        Fixture f = fixture(); generate(f, false);
        jdbc.update("UPDATE registrations SET status='REJECTED' WHERE id IN (?,?)", f.first(), f.second());
        var before = state(f);
        request(f, true).andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.code").value("protocol.no_confirmed_registrations"));
        assertEquals(before, state(f));
    }
    private record Fixture(UUID discipline, UUID first, UUID second, UUID version, PlatformUserPrincipal admin) { }
}
