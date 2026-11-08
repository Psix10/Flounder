package com.acme.sportplatform.integration.protocol;

import java.math.BigDecimal;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import org.springframework.test.web.servlet.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import org.springframework.jdbc.core.JdbcTemplate;
import com.acme.sportplatform.support.AbstractPostgresIntegrationTest;
import com.acme.sportplatform.identity.infrastructure.persistence.entity.*;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.*;
import com.acme.sportplatform.identity.infrastructure.security.PlatformUserPrincipal;
import com.acme.sportplatform.events.infrastructure.persistence.entity.EventEntity;
import com.acme.sportplatform.events.infrastructure.persistence.repository.EventRepository;
import com.acme.sportplatform.competition.infrastructure.jpa.*;
import com.acme.sportplatform.sports.infrastructure.jpa.*;
import com.acme.sportplatform.protocol.infrastructure.jpa.StartProtocolSnapshotRepository;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;

/** Explicit launch only: mvn test -Dtest=ProtocolExperimentScenario -Dexperiment.size=60 */
@AutoConfigureMockMvc
class ProtocolExperimentScenario extends AbstractPostgresIntegrationTest {
    private static final UUID SPORT = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final List<Integer> ORDER = List.of(4, 5, 3, 6, 2, 7, 1, 8);
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository users;
    @Autowired private ProfileRepository profiles;
    @Autowired private EventRepository events;
    @Autowired private DisciplineTemplateRepository templates;
    @Autowired private EventDisciplineRepository disciplines;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private StartProtocolSnapshotRepository snapshots;

    @Test void exportExperimentRun() throws Exception {
        int size = Integer.parseInt(System.getProperty("experiment.size", "60"));
        int repetitions = Integer.parseInt(System.getProperty("experiment.repetitions", "3"));
        long seed = Long.parseLong(System.getProperty("experiment.seed", "424242"));
        assertTrue(size >= 8 && size <= 2000, "experiment.size must be 8..2000");
        assertTrue(repetitions >= 1 && repetitions <= 100, "experiment.repetitions must be 1..100");
        String label = size == 60 ? "S-60" : size == 200 ? "M-200" : "N-" + size;
        Path output = Path.of("target", "protocol-experiments", label + "-"
                + Instant.now().toString().replace(":", "-") + "-" + UUID.randomUUID().toString().substring(0, 8));
        Files.createDirectories(output);
        long fixtureStart = System.nanoTime();
        PlatformUserPrincipal admin = actor("PLATFORM_ADMIN");
        UUID discipline = discipline(admin, size + Math.max(3, size / 20) + 10);
        UUID version = rules(admin);
        Random random = new Random(seed);
        List<Row> rows = new ArrayList<>();
        BigDecimal previous = null;
        for (int i = 0; i < size; i++) {
            BigDecimal metric = (i % 10 == 9 && previous != null) ? previous
                    : BigDecimal.valueOf(2500 + random.nextInt(4501), 2);
            previous = metric;
            rows.add(registration(discipline, admin, i % 13 == 12 ? null : metric));
        }
        double fixtureMs = (System.nanoTime() - fixtureStart) / 1_000_000.0;
        List<String> measurements = new ArrayList<>();
        measurements.add("scenario,repetition,operation,duration_ms,unit_count,assignment_count,revision");
        ArrayNode scenarios = json.createArrayNode();
        scenario(output, scenarios, measurements, "baseline", rows, discipline, version, admin, repetitions, false);
        scenario(output, scenarios, measurements, "no_change", rows, discipline, version, admin, repetitions, true);
        int additions = Math.max(3, size / 20);
        for (int i = 0; i < additions; i++) rows.add(registration(discipline, admin,
                BigDecimal.valueOf(2400 + random.nextInt(4001), 2)));
        scenario(output, scenarios, measurements, "addition", rows, discipline, version, admin, repetitions, true);
        for (int i = 0; i < 2; i++) {
            Row r = rows.get(i);
            jdbc.update("UPDATE registrations SET status='REJECTED' WHERE id=?", r.registrationId());
            rows.set(i, new Row(r.registrationId(), "REJECTED", r.seedMetric()));
        }
        scenario(output, scenarios, measurements, "withdrawal", rows, discipline, version, admin, repetitions, true);
        Row changed = rows.get(size / 2);
        jdbc.update("""
                UPDATE registrations SET registration_meta=jsonb_set(registration_meta,
                    '{seedTimeSeconds}', '99.99'::jsonb) WHERE id=?
                """, changed.registrationId());
        rows.set(size / 2, new Row(changed.registrationId(), "CONFIRMED", new BigDecimal("99.99")));
        scenario(output, scenarios, measurements, "seed_change", rows, discipline, version, admin, repetitions, true);
        ObjectNode manifest = json.createObjectNode();
        manifest.put("schemaVersion", 1); manifest.put("label", label); manifest.put("seed", seed);
        manifest.put("baseSize", size); manifest.put("repetitions", repetitions);
        manifest.put("fixtureDurationMs", fixtureMs);
        manifest.put("timingScope", "MockMvc generation request including DB and snapshot; excludes fixture and response parsing");
        manifest.put("ruleSetVersionId", version.toString()); manifest.put("eventDisciplineId", discipline.toString());
        manifest.set("scenarios", scenarios);
        Files.writeString(output.resolve("measurements.csv"), String.join("\n", measurements) + "\n", StandardCharsets.UTF_8);
        write(output.resolve("manifest.json"), manifest);
        System.out.println("Protocol experiment exported: " + output.toAbsolutePath());
    }
    private void scenario(Path output, ArrayNode scenarios, List<String> measurements, String name,
            List<Row> rows, UUID discipline, UUID version, PlatformUserPrincipal admin,
            int repetitions, boolean existing) throws Exception {
        Path folder = output.resolve(name); Files.createDirectories(folder);
        ObjectNode input = json.createObjectNode(); input.put("schemaVersion", 1);
        input.put("capacity", 8); input.put("heatOrder", "SLOWEST_FIRST"); input.put("partialHeat", "FIRST");
        input.put("ntPlacement", "WEAKEST"); input.set("positionOrder", json.valueToTree(ORDER));
        input.set("allowedPositions", json.valueToTree(List.of(1,2,3,4,5,6,7,8)));
        input.set("registrations", json.valueToTree(rows));
        write(folder.resolve("input.json"), input);
        ObjectNode description = json.createObjectNode(); description.put("name", name);
        description.put("input", name + "/input.json"); ArrayNode responses = description.putArray("protocols");
        ArrayNode snapshotPaths = description.putArray("snapshots");
        for (int rep = 1; rep <= repetitions; rep++) {
            boolean regenerate = existing || rep > 1;
            byte[] body = json.writeValueAsBytes(Map.of("regenerate", regenerate, "ruleSetVersionId", version));
            long start = System.nanoTime();
            MvcResult response = mvc.perform(post("/api/v1/event-disciplines/{id}/start-protocol/generate", discipline)
                    .with(user(admin)).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
            double ms = (System.nanoTime() - start) / 1_000_000.0;
            assertEquals(200, response.getResponse().getStatus(), response.getResponse().getContentAsString());
            JsonNode protocol = json.readTree(response.getResponse().getContentAsString());
            String filename = String.format(Locale.ROOT, "protocol-%02d.json", rep);
            write(folder.resolve(filename), protocol); responses.add(name + "/" + filename);
            Integer revision = jdbc.queryForObject("SELECT max(revision_no) FROM start_protocol_snapshots WHERE event_discipline_id=?",
                    Integer.class, discipline);
            assertNotNull(revision);
            var snapshot = snapshots.findFirstByEventDisciplineIdOrderByRevisionNoDesc(discipline).orElseThrow();
            assertEquals(revision, snapshot.getRevisionNo());
            ObjectNode audit = json.createObjectNode();
            audit.put("id", snapshot.getId().toString()); audit.put("revision", revision);
            audit.put("operation", snapshot.getOperation()); audit.put("ruleSetVersionId", version.toString());
            audit.set("input", snapshot.getInput()); audit.set("protocol", snapshot.getProtocol());
            String snapshotName = String.format(Locale.ROOT, "snapshot-%02d.json", rep);
            write(folder.resolve(snapshotName), audit); snapshotPaths.add(name + "/" + snapshotName);
            measurements.add(String.format(Locale.ROOT, "%s,%d,%s,%.6f,%d,%d,%d", name, rep,
                    regenerate ? "REGENERATE" : "GENERATE", ms, protocol.path("unitCount").asInt(),
                    protocol.path("assignmentCount").asInt(), revision));
        }
        scenarios.add(description);
    }
    private void write(Path file, JsonNode data) throws Exception {
        json.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), data);
    }
    private PlatformUserPrincipal actor(String role) {
        var u = new UserEntity(); u.setEmail("experiment-" + UUID.randomUUID() + "@example.com");
        u.setPasswordHash("test-password-hash"); u.setStatus("active"); u = users.saveAndFlush(u);
        return new PlatformUserPrincipal(u.getId(), u.getEmail(), "", "active",
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
    private UUID discipline(PlatformUserPrincipal admin, int limit) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        var event = new EventEntity(); event.setId(UUID.randomUUID()); event.setCreatedByUserId(admin.getUserId());
        event.setOrganizationId(UUID.randomUUID()); event.setVenueId(UUID.randomUUID()); event.setSportId(SPORT);
        event.setRegulationVersionId(UUID.randomUUID()); event.setTitle("Protocol experiment"); event.setDescription("Synthetic fixture");
        event.setRegistrationOpenAt(now.minusDays(1)); event.setRegistrationCloseAt(now.plusDays(10));
        event.setEventStartAt(now.plusDays(20)); event.setEventEndAt(now.plusDays(21));
        event.setStatus("REGISTRATION_OPEN"); event.setPublicSlug("experiment-" + UUID.randomUUID());
        event.setSettingsJson("{}"); event.setCreatedAt(now); event.setUpdatedAt(now); events.saveAndFlush(event);
        var t = new DisciplineTemplateEntity(); t.setId(UUID.randomUUID()); t.setSportId(SPORT);
        t.setCode("experiment-" + UUID.randomUUID()); t.setName("50m Freestyle");
        t.setCompetitionFormat("INDIVIDUAL"); t.setUnitType("HEAT"); t.setResultType("TIME");
        t.setRankingStrategy("ASC"); t.setDefaultMeta("{}"); templates.saveAndFlush(t);
        var d = new EventDisciplineEntity(); d.setId(UUID.randomUUID()); d.setEventId(event.getId());
        d.setDisciplineTemplateId(t.getId()); d.setCode("50m"); d.setName("50m Freestyle");
        d.setCompetitionFormat("INDIVIDUAL"); d.setUnitType("HEAT"); d.setResultType("TIME");
        d.setRankingStrategy("ASC"); d.setParticipantLimit(limit); d.setEntryFeeAmount(BigDecimal.ZERO);
        d.setEntryFeeCurrency("RUB"); d.setStatus("PUBLISHED"); d.setSettingsJson("{}");
        d.setCreatedAt(now); d.setUpdatedAt(now); disciplines.saveAndFlush(d); return d.getId();
    }
    private Row registration(UUID discipline, PlatformUserPrincipal admin, BigDecimal metric) throws Exception {
        var participant = actor("PARTICIPANT"); var p = new ProfileEntity(); p.setUserId(participant.getUserId());
        p.setFirstName("Ivan"); p.setLastName("Petrov"); p.setBirthDate(LocalDate.parse("2004-05-10"));
        p.setGender("MALE"); p.setCountryCode("RU"); p.setCity("Moscow"); p.setClubName("Synthetic club");
        p.setSportMeta(Map.of("sportRank", "FIRST_CATEGORY")); profiles.saveAndFlush(p);
        // NT is prepared after valid registration; this is a fixture, not a new registration API contract.
        JsonNode response = call(post("/api/v1/registrations").with(user(participant)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("eventDisciplineId", discipline, "registrationMeta",
                        Map.of("seedTimeSeconds", metric == null ? new BigDecimal("30.00") : metric,
                                "emergencyContactPhone", "+79990000000")))), 200);
        UUID id = UUID.fromString(response.path("id").asText());
        call(post("/api/v1/registrations/{id}/review", id).with(user(admin))
                .contentType(MediaType.APPLICATION_JSON).content("{\"decision\":\"CONFIRMED\",\"reviewNote\":\"Experiment\"}"), 200);
        if (metric == null) jdbc.update("""
                UPDATE registrations SET registration_meta=jsonb_set(registration_meta,
                    '{seedTimeSeconds}', 'null'::jsonb) WHERE id=?
                """, id);
        return new Row(id, "CONFIRMED", metric);
    }
    private UUID rules(PlatformUserPrincipal admin) throws Exception {
        ObjectNode request = (ObjectNode) json.readTree("""
                {"sportId":"10000000-0000-0000-0000-000000000001","code":"placeholder","name":"Experiment rules",
                "competitionFormat":"INDIVIDUAL","unitType":"HEAT","blocks":[
                {"orderIndex":0,"executorCode":"ELIGIBILITY_CONFIRMED_ONLY","config":{}},
                {"orderIndex":1,"executorCode":"SEED_BY_METRIC_ASC_NULLS_LAST","config":{"metricKey":"seedTimeSeconds"}},
                {"orderIndex":2,"executorCode":"GROUP_BY_CAPACITY","config":{"capacity":8,"unitLabelPrefix":"Heat",
                "heatOrder":"SLOWEST_FIRST","partialHeat":"FIRST","ntPlacement":"WEAKEST"}},
                {"orderIndex":3,"executorCode":"ASSIGN_CENTER_OUT","config":{
                "allowedPositions":[1,2,3,4,5,6,7,8],"positionOrder":[4,5,3,6,2,7,1,8]}}]}
                """);
        request.put("code", "experiment-" + UUID.randomUUID());
        JsonNode created = call(post("/api/v1/protocol-rule-sets").with(user(admin))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(request)), 201);
        UUID version = UUID.fromString(created.path("ruleSetVersionId").asText());
        call(post("/api/v1/protocol-rule-sets/{id}/versions/{version}/publish",
                created.path("id").asText(), version).with(user(admin)), 200);
        return version;
    }
    private JsonNode call(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            int expected) throws Exception {
        MvcResult result = mvc.perform(request).andReturn();
        assertEquals(expected, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        String body = result.getResponse().getContentAsString();
        return body.isBlank() ? json.createObjectNode() : json.readTree(body);
    }
    private record Row(UUID registrationId, String status, BigDecimal seedMetric) { }
}
