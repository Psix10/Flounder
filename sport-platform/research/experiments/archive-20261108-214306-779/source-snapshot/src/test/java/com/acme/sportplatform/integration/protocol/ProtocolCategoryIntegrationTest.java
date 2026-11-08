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
class ProtocolCategoryIntegrationTest extends AbstractPostgresIntegrationTest {
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
        return rule(admin, eligibility, null);
    }
    private UUID rule(PlatformUserPrincipal admin, JsonNode eligibility, JsonNode categories) throws Exception {
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
        if (categories != null) ((ObjectNode) request.path("blocks").get(2).path("config")).set("categories", categories);
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



    private Fixture categorized(Fixture f, boolean shared) throws Exception {
        var categories=json.readTree("""
            [{"code":"MEN","seedingGroupCode":"M","eligibility":{"allowedGenders":["MALE"]}},
             {"code":"WOMEN","seedingGroupCode":"F","eligibility":{"allowedGenders":["FEMALE"]}}]
            """);
        if(shared) for(var c:categories) ((ObjectNode)c).put("seedingGroupCode","OPEN");
        UUID version=rule(f.admin(),json.createObjectNode(),categories);
        return new Fixture(f.discipline(),f.first(),f.second(),version,f.admin());
    }
    private void female(UUID registration) {
        jdbc.update("UPDATE registrations SET participant_snapshot=jsonb_set(participant_snapshot,'{gender}','\"FEMALE\"'::jsonb) WHERE id=?",registration);
    }
    @Test void separatesAndPersistsCategoriesInPostGenerateGetAndSnapshot() throws Exception {
        var f=categorized(fixture(),false);female(f.second());var response=generate(f,false);
        assertEquals(2,response.path("unitCount").asInt());
        Set<String> seen=new HashSet<>();
        for(var unit:response.path("units")) {
            assertEquals(1,unit.path("assignments").size());var a=unit.path("assignments").get(0);
            assertFalse(a.path("categoryCode").asText().isBlank());seen.add(a.path("categoryCode").asText());
        }
        assertEquals(Set.of("MEN","WOMEN"),seen);
        var stored=json.readTree(mvc.perform(get("/api/v1/event-disciplines/{id}/start-protocol",f.discipline()).with(user(f.admin())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        for(var unit:stored.path("units"))for(var a:unit.path("assignments")) {
            var row=jdbc.queryForMap("SELECT category_code,seeding_group_code FROM competition_unit_entries WHERE registration_id=?",UUID.fromString(a.path("registrationId").asText()));
            assertEquals(row.get("category_code"),a.path("categoryCode").asText());
            assertEquals(row.get("seeding_group_code"),a.path("seedingGroupCode").asText());
        }
        var snapshot=snapshots.findFirstByEventDisciplineIdOrderByRevisionNoDesc(f.discipline()).orElseThrow();
        assertEquals("MEN",snapshot.getProtocol().path("units").get(0).path("assignments").get(0).path("categoryCode").asText());
        assertEquals(2,snapshot.getInput().path("rules").path("blocks").get(2).path("config").path("categories").size());
    }
    @Test void sharedGroupPersistsDifferentCategoriesInOneHeat() throws Exception {
        var f=categorized(fixture(),true);female(f.second());var response=generate(f,false);
        assertEquals(1,response.path("unitCount").asInt());
        Set<String> codes=new HashSet<>();for(var a:response.path("units").get(0).path("assignments")) {
            assertEquals("OPEN",a.path("seedingGroupCode").asText());codes.add(a.path("categoryCode").asText());
        }
        assertEquals(Set.of("MEN","WOMEN"),codes);
    }
    @Test void noMatchReturnsRegistrationIdAndDoesNotWrite() throws Exception {
        var f=categorized(fixture(),false);
        jdbc.update("UPDATE registrations SET participant_snapshot='{}'::jsonb WHERE id=?",f.first());var before=state(f);
        request(f,false).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("protocol.category_violation"))
                .andExpect(jsonPath("$.violations[0].field").value("registrations["+f.first()+"].category"))
                .andExpect(jsonPath("$.violations[0].message",org.hamcrest.Matchers.containsString("NO_MATCHING_CATEGORY")));
        assertEquals(before,state(f));
    }
    @Test void ambiguityOnRegenerationPreservesPreviousUnitsAndSnapshot() throws Exception {
        var original=fixture();generate(original,false);
        var categories=json.readTree("""
            [{"code":"A","seedingGroupCode":"OPEN","eligibility":{}},
             {"code":"B","seedingGroupCode":"OPEN","eligibility":{}}]
            """);
        var f=new Fixture(original.discipline(),original.first(),original.second(),rule(original.admin(),json.createObjectNode(),categories),original.admin());
        var before=state(f);
        request(f,true).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("protocol.category_violation"))
                .andExpect(jsonPath("$.violations[0].message",org.hamcrest.Matchers.containsString("AMBIGUOUS_CATEGORY")));
        assertEquals(before,state(f));
    }
    @Test void regenerationRetainsPersistedCategoryMembership() throws Exception {
        var f=categorized(fixture(),false);female(f.second());generate(f,false);var regenerated=generate(f,true);
        assertEquals(2,regenerated.path("unitCount").asInt());
        assertEquals(2,snapshots.findFirstByEventDisciplineIdOrderByRevisionNoDesc(f.discipline()).orElseThrow().getRevisionNo());
        assertEquals(2,jdbc.queryForObject("""
            SELECT count(*) FROM competition_unit_entries e JOIN competition_units u ON u.id=e.competition_unit_id
            WHERE u.event_discipline_id=? AND e.category_code IS NOT NULL AND e.seeding_group_code IS NOT NULL
            """,Integer.class,f.discipline()));
    }
@Test void sessionUseCaseCannotBypassCategories() throws Exception {
    var f=categorized(fixture(),false);generate(f,false);
    jdbc.update("UPDATE registrations SET participant_snapshot='{}'::jsonb WHERE id=?",f.first());
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
    try {assertThrows(com.acme.sportplatform.protocol.application.ProtocolCategoryException.class,()->sessionUseCase.execute(command));}
    finally {SecurityContextHolder.clearContext();}
    assertEquals(before,state(f));assertEquals(count,jdbc.queryForObject("SELECT count(*) FROM session_protocol_snapshots WHERE event_id=?",Long.class,event));
}


    @Test void sessionGenerationPersistsCategoryAndTimedDrafts() throws Exception {
        var f=categorized(fixture(),false);female(f.second());
        UUID event=jdbc.queryForObject("SELECT event_id FROM event_disciplines WHERE id=?",UUID.class,f.discipline());
        UUID session=UUID.randomUUID();Instant start=Instant.parse("2026-11-22T06:30:00Z");
        var command=new com.acme.sportplatform.protocol.application.schedule.GenerateSessionProtocolCommand(event,false,
                new com.acme.sportplatform.protocol.domain.schedule.ProtocolScheduleModel.TimingPolicy(Duration.ofSeconds(600),Duration.ofSeconds(30),
                        com.acme.sportplatform.protocol.domain.schedule.ProtocolScheduleModel.Origin.EXPERIMENTAL,"test"),
                List.of(new com.acme.sportplatform.protocol.domain.schedule.ProtocolScheduleModel.Session(session,
                        new com.acme.sportplatform.protocol.domain.schedule.ProtocolScheduleModel.Window(start,start.plusSeconds(3600)),List.of())),
                List.of(new com.acme.sportplatform.protocol.application.schedule.GenerateSessionProtocolCommand.Discipline(f.discipline(),f.version(),session,Duration.ofSeconds(120),"test")));
        var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(new UsernamePasswordAuthenticationToken(f.admin(),"",f.admin().getAuthorities()));
        SecurityContextHolder.setContext(context);
        try {
            var result=sessionUseCase.execute(command);
            assertEquals(2,result.disciplines().get(0).units().size());
            for(var unit:result.disciplines().get(0).units())for(var assignment:unit.assignments()) {
                assertNotNull(assignment.categoryCode());assertNotNull(assignment.seedingGroupCode());
            }
        } finally {SecurityContextHolder.clearContext();}
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM competition_units WHERE event_discipline_id=? AND scheduled_at IS NOT NULL",Integer.class,f.discipline()));
    }
    @Test void databaseRejectsHalfPopulatedCategoryPair() throws Exception {
        var f=categorized(fixture(),false);generate(f,false);var before=state(f);
        assertThrows(DataAccessException.class,()->jdbc.update("UPDATE competition_unit_entries SET seeding_group_code=NULL WHERE registration_id=?",f.first()));
        assertEquals(before,state(f));
    }
    @Autowired private com.acme.sportplatform.results.ResultsProtocolGateway gateway;
    @Test void gatewayRejectsDifferentSeedingGroupsWithinOneHeat() throws Exception {
        var f=fixture();var before=state(f);
        var draft=new com.acme.sportplatform.results.ResultsCompetitionUnitDraft("Heat 1",1,"DRAFT",List.of(
                new com.acme.sportplatform.results.ResultsCompetitionUnitEntryDraft(f.first(),0,"MEN","M"),
                new com.acme.sportplatform.results.ResultsCompetitionUnitEntryDraft(f.second(),1,"WOMEN","F")));
        assertThrows(com.acme.sportplatform.common.exception.BusinessException.class,()->gateway.createDraftStartProtocol(f.discipline(),List.of(draft)));
        assertEquals(before,state(f));
    }

    private record Fixture(UUID discipline, UUID first, UUID second, UUID version, PlatformUserPrincipal admin) { }
}
