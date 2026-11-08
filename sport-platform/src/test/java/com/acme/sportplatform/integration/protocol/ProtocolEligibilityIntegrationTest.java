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
class ProtocolEligibilityIntegrationTest extends AbstractPostgresIntegrationTest {
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
    @Autowired private com.acme.sportplatform.protocol.application.GenerateSessionProtocolUseCase sessionUseCase;

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
        return rule(admin, json.createObjectNode());
    }
    private UUID rule(PlatformUserPrincipal admin, JsonNode eligibility) throws Exception {
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
        ((ObjectNode) request.path("blocks").get(0)).set("config", eligibility);
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


private Fixture strict(Fixture f) throws Exception {
    UUID version=rule(f.admin(),json.readTree("{\"ageMethod\":\"ON_DATE\",\"ageReferenceDate\":\"2026-11-22\",\"minAge\":18,\"maxAge\":25,\"allowedGenders\":[\"MALE\"]}"));
    return new Fixture(f.discipline(),f.first(),f.second(),version,f.admin());
}
@Test void acceptsValidParticipantsUsingPublishedRulesAndCapturesSnapshot() throws Exception {
    var f=strict(fixture());var protocol=generate(f,false);assertEquals(2,protocol.path("assignmentCount").asInt());
    var latest=snapshots.findFirstByEventDisciplineIdOrderByRevisionNoDesc(f.discipline()).orElseThrow();
    var registration=latest.getInput().path("confirmedRegistrations").get(0);
    assertTrue(registration.path("participantSnapshot").isTextual());
    assertEquals("MALE",json.readTree(registration.path("participantSnapshot").asText()).path("gender").asText());
}
@Test void rejectsWrongGenderWithStructuredHttpErrorAndNoWrites() throws Exception {
    var f=strict(fixture());
    jdbc.update("UPDATE registrations SET participant_snapshot=jsonb_set(participant_snapshot,'{gender}','\"FEMALE\"'::jsonb) WHERE id=?",f.first());
    var before=state(f);
    request(f,false).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("protocol.eligibility_violation"))
            .andExpect(jsonPath("$.violations[0].field").value("registrations["+f.first()+"].participantSnapshot.gender"))
            .andExpect(jsonPath("$.violations[0].message",org.hamcrest.Matchers.containsString("GENDER_NOT_ALLOWED")));
    assertEquals(before,state(f));
}
@Test void rejectsTooYoungParticipantOnRegenerationAndPreservesPriorProtocol() throws Exception {
    var f=strict(fixture());generate(f,false);
    jdbc.update("UPDATE registrations SET participant_snapshot=jsonb_set(participant_snapshot,'{birthDate}','\"2015-11-22\"'::jsonb) WHERE id=?",f.first());
    var before=state(f);
    request(f,true).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("protocol.eligibility_violation"))
            .andExpect(jsonPath("$.violations[0].message",org.hamcrest.Matchers.containsString("AGE_OUT_OF_RANGE")));
    assertEquals(before,state(f));
}
@Test void missingParticipantFieldsAreNotSilentlySkipped() throws Exception {
    var f=strict(fixture());jdbc.update("UPDATE registrations SET participant_snapshot='{}'::jsonb WHERE id=?",f.first());var before=state(f);
    var response=json.readTree(request(f,false).andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString());
    assertEquals("protocol.eligibility_violation",response.path("code").asText());assertEquals(2,response.path("violations").size());
    assertEquals(before,state(f));
}
@Test void sessionUseCaseCannotBypassEligibilityChecks() throws Exception {
    var f=strict(fixture());generate(f,false);
    jdbc.update("UPDATE registrations SET participant_snapshot=jsonb_set(participant_snapshot,'{gender}','\"FEMALE\"'::jsonb) WHERE id=?",f.first());
    var before=state(f);UUID event=jdbc.queryForObject("SELECT event_id FROM event_disciplines WHERE id=?",UUID.class,f.discipline());
    Long count=jdbc.queryForObject("SELECT count(*) FROM session_protocol_snapshots WHERE event_id=?",Long.class,event);
    UUID session=UUID.randomUUID();Instant start=Instant.parse("2026-11-22T06:30:00Z");
    var command=new com.acme.sportplatform.protocol.application.schedule.GenerateSessionProtocolCommand(event,true,
            new com.acme.sportplatform.protocol.domain.schedule.ProtocolScheduleModel.TimingPolicy(Duration.ofSeconds(600),Duration.ofSeconds(30),
                    com.acme.sportplatform.protocol.domain.schedule.ProtocolScheduleModel.Origin.EXPERIMENTAL,"test"),
            List.of(new com.acme.sportplatform.protocol.domain.schedule.ProtocolScheduleModel.Session(session,
                    new com.acme.sportplatform.protocol.domain.schedule.ProtocolScheduleModel.Window(start,start.plusSeconds(3600)),List.of())),
            List.of(new com.acme.sportplatform.protocol.application.schedule.GenerateSessionProtocolCommand.Discipline(f.discipline(),f.version(),session,Duration.ofSeconds(120),"test")));
    var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(new UsernamePasswordAuthenticationToken(f.admin(),"",f.admin().getAuthorities()));
    SecurityContextHolder.setContext(context);
    try {assertThrows(com.acme.sportplatform.protocol.application.ProtocolEligibilityException.class,()->sessionUseCase.execute(command));}
    finally {SecurityContextHolder.clearContext();}
    assertEquals(before,state(f));assertEquals(count,jdbc.queryForObject("SELECT count(*) FROM session_protocol_snapshots WHERE event_id=?",Long.class,event));
}

    private record Fixture(UUID discipline, UUID first, UUID second, UUID version, PlatformUserPrincipal admin) { }
}
