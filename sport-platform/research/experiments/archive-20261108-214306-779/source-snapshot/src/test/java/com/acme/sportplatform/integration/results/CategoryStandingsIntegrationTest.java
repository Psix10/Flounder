package com.acme.sportplatform.integration.results;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.acme.sportplatform.competition.domain.EventDisciplineStatus;
import com.acme.sportplatform.competition.infrastructure.jpa.EventDisciplineEntity;
import com.acme.sportplatform.competition.infrastructure.jpa.EventDisciplineRepository;
import com.acme.sportplatform.events.domain.EventStatus;
import com.acme.sportplatform.events.infrastructure.persistence.entity.EventEntity;
import com.acme.sportplatform.events.infrastructure.persistence.repository.EventOperatorAssignmentRepository;
import com.acme.sportplatform.events.infrastructure.persistence.repository.EventRepository;
import com.acme.sportplatform.identity.infrastructure.persistence.entity.ProfileEntity;
import com.acme.sportplatform.identity.infrastructure.persistence.entity.RoleEntity;
import com.acme.sportplatform.identity.infrastructure.persistence.entity.UserEntity;
import com.acme.sportplatform.identity.infrastructure.persistence.entity.UserRoleAssignmentEntity;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.ProfileRepository;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.UserRepository;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.UserRoleAssignmentRepository;
import com.acme.sportplatform.identity.infrastructure.security.PlatformUserPrincipal;
import com.acme.sportplatform.results.infrastructure.jpa.CompetitionUnitEntity;
import com.acme.sportplatform.results.infrastructure.jpa.CompetitionUnitEntryEntity;
import com.acme.sportplatform.results.infrastructure.jpa.CompetitionUnitEntryRepository;
import com.acme.sportplatform.results.infrastructure.jpa.CompetitionUnitRepository;
import com.acme.sportplatform.results.infrastructure.jpa.ResultEntity;
import com.acme.sportplatform.results.infrastructure.jpa.ResultRepository;
import com.acme.sportplatform.sports.infrastructure.jpa.DisciplineTemplateEntity;
import com.acme.sportplatform.sports.infrastructure.jpa.DisciplineTemplateRepository;
import com.acme.sportplatform.support.AbstractPostgresIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@AutoConfigureMockMvc
class CategoryStandingsIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final UUID SWIMMING_SPORT_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProfileRepository profileRepository;

    @Autowired
    private UserRoleAssignmentRepository userRoleAssignmentRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventOperatorAssignmentRepository operatorAssignmentRepository;

    @Autowired
    private DisciplineTemplateRepository disciplineTemplateRepository;

    @Autowired
    private EventDisciplineRepository eventDisciplineRepository;

    @Autowired
    private CompetitionUnitRepository competitionUnitRepository;

    @Autowired
    private CompetitionUnitEntryRepository entryRepository;

    @Autowired
    private ResultRepository resultRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    private final Map<UUID, PlatformUserPrincipal> operatorsByEvent =
            new HashMap<>();


    private void category(Fixture f,String code,String group) {
        var entry=entryRepository.findById(f.entryId()).orElseThrow();
        entry.setCategoryCode(code);entry.setSeedingGroupCode(group);entryRepository.saveAndFlush(entry);
    }
    private Fixture extra(Fixture f,int sequence,String code,String group,String value,boolean published) throws Exception {
        var next=createAdditionalHeat(f.eventId(),f.disciplineId(),sequence);category(next,code,group);
        recordAndRecalculate(next,value,"PENDING","ASC").andExpect(status().isOk());
        if(published) publish(next).andExpect(status().isNoContent());
        return next;
    }
    private com.fasterxml.jackson.databind.JsonNode standings(Fixture f) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/v1/public/events/{event}/disciplines/{discipline}/standings",f.eventId(),f.disciplineId()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    @Test void categoryPlacesCombinePublishedHeatsAndRemainIndependent() throws Exception {
        var f=createFixture();category(f,"MEN","M");recordAndRecalculate(f,"30","PENDING","ASC").andExpect(status().isOk());publish(f).andExpect(status().isNoContent());
        extra(f,2,"WOMEN","F","25",true);extra(f,3,"MEN","M","29",true);
        var e=standings(f).path("entries");assertEquals(3,e.size());
        assertEquals("WOMEN",e.get(0).path("categoryCode").asText());assertEquals(1,e.get(0).path("categoryPlace").asInt());
        assertEquals(1,e.get(1).path("categoryPlace").asInt());assertEquals(2,e.get(2).path("categoryPlace").asInt());
        assertEquals(3,e.get(2).path("overallPlace").asInt());assertEquals(1,e.get(2).path("unitPlace").asInt());
    }
    @Test void sharedHeatHasSeparateScoringCategories() throws Exception {
        var f=createFixture();category(f,"MEN","OPEN");
        var entry=new CompetitionUnitEntryEntity();entry.setId(UUID.randomUUID());entry.setCompetitionUnitId(f.unitId());
        entry.setRegistrationId(createConfirmedRegistration(f.disciplineId()));entry.setLaneOrPosition(2);
        entry.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));entry.setCategoryCode("WOMEN");entry.setSeedingGroupCode("OPEN");entryRepository.saveAndFlush(entry);
        var second=new Fixture(f.eventId(),f.disciplineId(),f.unitId(),entry.getId());
        recordAndRecalculate(f,"30","PENDING","ASC").andExpect(status().isOk());
        recordAndRecalculate(second,"28","PENDING","ASC").andExpect(status().isOk());publish(f).andExpect(status().isNoContent());
        var e=standings(f).path("entries");assertEquals(2,e.size());
        assertEquals(1,e.get(0).path("categoryPlace").asInt());assertEquals(1,e.get(1).path("categoryPlace").asInt());
        assertEquals(2,e.get(1).path("unitPlace").asInt());assertEquals("OPEN",e.get(1).path("seedingGroupCode").asText());
    }
    @Test void equivalentTimesShareCategoryPlaceAcrossHeats() throws Exception {
        var f=createFixture();category(f,"A","G1");recordAndRecalculate(f,"1:08.54","PENDING","ASC").andExpect(status().isOk());publish(f).andExpect(status().isNoContent());
        extra(f,2,"A","G2","68.540",true);extra(f,3,"A","G3","70",true);
        var e=standings(f).path("entries");assertEquals(1,e.get(0).path("categoryPlace").asInt());
        assertEquals(1,e.get(1).path("categoryPlace").asInt());assertEquals(3,e.get(2).path("categoryPlace").asInt());
    }
    @Test void draftHeatIsExcludedAndReadingDoesNotWriteResults() throws Exception {
        var f=createFixture();category(f,"A","G");recordAndRecalculate(f,"30","PENDING","ASC").andExpect(status().isOk());publish(f).andExpect(status().isNoContent());
        extra(f,2,"A","G","20",false);
        var before=findResult(f);var version=before.getVersion();var updated=before.getUpdatedAt();var place=before.getFinalPlace();
        var first=standings(f);var second=standings(f);assertEquals(first,second);assertEquals(1,first.path("entries").size());
        var after=findResult(f);assertEquals(version,after.getVersion());assertEquals(updated,after.getUpdatedAt());assertEquals(place,after.getFinalPlace());
    }
    @Test void specialStatusKeepsCategoryWithoutCategoryPlace() throws Exception {
        var f=createFixture();category(f,"A","G");recordOnly(f,"0","DNS").andExpect(status().isOk());publish(f).andExpect(status().isNoContent());
        var e=standings(f).path("entries").get(0);assertEquals("A",e.path("categoryCode").asText());
        assertTrue(e.path("categoryPlace").isMissingNode()||e.path("categoryPlace").isNull());
        assertTrue(e.path("overallPlace").isMissingNode()||e.path("overallPlace").isNull());
    }
    @Test void mixedLegacyAndCategorizedPublishedResultsAreRejected() throws Exception {
        var f=createFixture();category(f,"A","G");recordAndRecalculate(f,"30","PENDING","ASC").andExpect(status().isOk());publish(f).andExpect(status().isNoContent());
        extra(f,2,null,null,"29",true);
        mockMvc.perform(get("/api/v1/public/events/{event}/disciplines/{discipline}/standings",f.eventId(),f.disciplineId()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("results.mixed_category_assignments"));
    }
    @Test void legacyResultsKeepOldOverallPlacesWithoutCategoryPlaces() throws Exception {
        var f=createFixture();recordAndRecalculate(f,"30","PENDING","ASC").andExpect(status().isOk());publish(f).andExpect(status().isNoContent());
        extra(f,2,null,null,"28",true);var e=standings(f).path("entries");assertEquals(2,e.size());
        assertEquals(1,e.get(0).path("overallPlace").asInt());assertEquals(2,e.get(1).path("overallPlace").asInt());
        assertTrue(e.get(0).path("categoryPlace").isMissingNode()||e.get(0).path("categoryPlace").isNull());
    }
    @Test void publicResultsProjectionReturnsStoredCategoryAndGroup() throws Exception {
        var f=createFixture();category(f,"SAVED_CATEGORY","SAVED_GROUP");recordAndRecalculate(f,"30","PENDING","ASC").andExpect(status().isOk());publish(f).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/events/{event}/disciplines/{discipline}/results",f.eventId(),f.disciplineId()).with(user(operatorFor(f))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.units[0].entries[0].categoryCode").value("SAVED_CATEGORY"))
                .andExpect(jsonPath("$.units[0].entries[0].seedingGroupCode").value("SAVED_GROUP"));
    }
    private ResultActions recordOnly(
            Fixture fixture,
            String rawValue,
            String resultStatus
    ) throws Exception {
        return mockMvc.perform(post("/api/v1/results")
                .with(user(operatorFor(fixture)))
                .param("resultType", "TIME")
                .contentType(MediaType.APPLICATION_JSON)
                .content(resultJson(
                        fixture.entryId(),
                        rawValue,
                        resultStatus
                )));
    }

    private ResultActions recordAndRecalculate(
            Fixture fixture,
            String rawValue,
            String resultStatus,
            String rankingStrategy
    ) throws Exception {
        return mockMvc.perform(post(
                        "/api/v1/competition-units/{unitId}/results",
                        fixture.unitId()
                )
                .with(user(operatorFor(fixture)))
                .param("resultType", "TIME")
                .param("rankingStrategy", rankingStrategy)
                .contentType(MediaType.APPLICATION_JSON)
                .content(resultJson(
                        fixture.entryId(),
                        rawValue,
                        resultStatus
                )));
    }

    private ResultActions publish(Fixture fixture) throws Exception {
        return mockMvc.perform(post(
                        "/api/v1/competition-units/{unitId}/publication",
                        fixture.unitId()
                )
                .with(user(operatorFor(fixture)))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "published": true
                        }
                        """));
    }

    private String resultJson(
            UUID entryId,
            String rawValue,
            String resultStatus
    ) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "competitionUnitEntryId", entryId,
                "rawValue", rawValue,
                "status", resultStatus
        ));
    }

    private ResultEntity findResult(Fixture fixture) {
        return resultRepository
                .findByCompetitionUnitEntryId(fixture.entryId())
                .orElseThrow();
    }

    private void assertResultUnchanged(
            Fixture fixture,
            String expectedRawValue,
            Long expectedVersion,
            OffsetDateTime expectedUpdatedAt
    ) {
        ResultEntity saved = findResult(fixture);

        assertEquals(expectedRawValue, saved.getRawValue());
        assertEquals("TIME", saved.getResultType());
        assertEquals("VALID", saved.getStatus());
        assertEquals(Integer.valueOf(1), saved.getFinalPlace());
        assertEquals(expectedVersion, saved.getVersion());
        assertEquals(expectedUpdatedAt, saved.getUpdatedAt());
    }

    private PlatformUserPrincipal operatorFor(Fixture fixture) {
        PlatformUserPrincipal existing =
                operatorsByEvent.get(fixture.eventId());

        if (existing != null) {
            return existing;
        }

        UserEntity operator = createUser("results-operator");
        grantGlobalRole(operator.getId(), "OPERATOR");

        new TransactionTemplate(transactionManager)
                .executeWithoutResult(transactionStatus ->
                        operatorAssignmentRepository.insertIfAbsent(
                                UUID.randomUUID(),
                                fixture.eventId(),
                                operator.getId(),
                                operator.getId(),
                                OffsetDateTime.now(ZoneOffset.UTC)
                        )
                );

        PlatformUserPrincipal operatorPrincipal =
                principal(operator, "OPERATOR");

        operatorsByEvent.put(
                fixture.eventId(),
                operatorPrincipal
        );

        return operatorPrincipal;
    }

    private void grantGlobalRole(UUID userId, String roleCode) {
        new TransactionTemplate(transactionManager)
                .executeWithoutResult(transactionStatus -> {
                    List<RoleEntity> roles = entityManager.createQuery(
                                    """
                                    select role
                                    from RoleEntity role
                                    where upper(role.code) = :code
                                    """,
                                    RoleEntity.class
                            )
                            .setParameter("code", roleCode)
                            .getResultList();

                    RoleEntity role;

                    if (roles.isEmpty()) {
                        role = new RoleEntity();
                        role.setCode(roleCode);
                        role.setName(roleCode);

                        entityManager.persist(role);
                        entityManager.flush();
                    } else {
                        role = roles.get(0);
                    }

                    UserRoleAssignmentEntity assignment =
                            new UserRoleAssignmentEntity();

                    assignment.setUserId(userId);
                    assignment.setRoleId(role.getId());

                    userRoleAssignmentRepository.saveAndFlush(assignment);
                });
    }

    private UserEntity createUser(String emailPrefix) {
        UserEntity user = new UserEntity();
        user.setEmail(
                emailPrefix + "-" + UUID.randomUUID() + "@example.com"
        );
        user.setPasswordHash("test-password-hash");
        user.setStatus("active");

        return userRepository.saveAndFlush(user);
    }

    private Fixture createFixture() throws Exception {
        UUID eventId = createEvent();
        UUID templateId = createDisciplineTemplate();
        UUID disciplineId = createDiscipline(eventId, templateId);

        return createAdditionalHeat(eventId, disciplineId, 1);
    }

    private Fixture createAdditionalHeat(
            UUID eventId,
            UUID disciplineId,
            int sequenceNumber
    ) throws Exception {
        UUID registrationId = createConfirmedRegistration(disciplineId);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        CompetitionUnitEntity unit = new CompetitionUnitEntity();
        unit.setId(UUID.randomUUID());
        unit.setEventDisciplineId(disciplineId);
        unit.setLabel("Heat " + sequenceNumber);
        unit.setSequenceNumber(sequenceNumber);
        unit.setStatus("DRAFT");
        unit.setCreatedAt(now);
        unit.setUpdatedAt(now);

        UUID unitId = competitionUnitRepository
                .saveAndFlush(unit)
                .getId();

        CompetitionUnitEntryEntity entry = new CompetitionUnitEntryEntity();
        entry.setId(UUID.randomUUID());
        entry.setCompetitionUnitId(unitId);
        entry.setRegistrationId(registrationId);
        entry.setLaneOrPosition(1);
        entry.setCreatedAt(now);

        UUID entryId = entryRepository
                .saveAndFlush(entry)
                .getId();

        return new Fixture(
                eventId,
                disciplineId,
                unitId,
                entryId
        );
    }

    private UUID createConfirmedRegistration(UUID disciplineId)
            throws Exception {

        UserEntity participant = new UserEntity();
        participant.setEmail(
                "results-" + UUID.randomUUID() + "@example.com"
        );
        participant.setPhone(randomPhone());
        participant.setPasswordHash("test-password-hash");
        participant.setStatus("active");

        participant = userRepository.saveAndFlush(participant);

        ProfileEntity profile = new ProfileEntity();
        profile.setUserId(participant.getId());
        profile.setFirstName("Ivan");
        profile.setLastName("Petrov");
        profile.setBirthDate(LocalDate.parse("2004-05-10"));
        profile.setGender("MALE");
        profile.setCountryCode("RU");
        profile.setCity("Moscow");
        profile.setClubName("Aqua Team");
        profile.setSportMeta(Map.of("sportRank", "FIRST_CATEGORY"));

        profileRepository.saveAndFlush(profile);

        PlatformUserPrincipal participantPrincipal =
                principal(participant, "PARTICIPANT");

        String response = mockMvc.perform(post("/api/v1/registrations")
                        .with(user(participantPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "eventDisciplineId", disciplineId,
                                "registrationMeta", Map.of(
                                        "seedTimeSeconds", 28.11,
                                        "emergencyContactPhone", "+79990000000"
                                )
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String registrationId = JsonPath.read(response, "$.id");

        mockMvc.perform(post(
                        "/api/v1/registrations/{registrationId}/review",
                        registrationId
                )
                        .with(user(principal("ORGANIZER")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "decision": "CONFIRMED",
                                  "reviewNote": "Results integration test"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        return UUID.fromString(registrationId);
    }

    private UUID createEvent() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        EventEntity event = new EventEntity();
        event.setId(UUID.randomUUID());
        event.setCreatedByUserId(UUID.randomUUID());
        event.setOrganizationId(UUID.randomUUID());
        event.setVenueId(UUID.randomUUID());
        event.setSportId(SWIMMING_SPORT_ID);
        event.setRegulationVersionId(UUID.randomUUID());
        event.setTitle("Results IT Event");
        event.setDescription("Fixture for results integration tests");
        event.setRegistrationOpenAt(now.minusDays(1));
        event.setRegistrationCloseAt(now.plusDays(20));
        event.setEventStartAt(now.plusDays(30));
        event.setEventEndAt(now.plusDays(31));
        event.setStatus(EventStatus.REGISTRATION_OPEN.name());
        event.setPublicSlug("results-it-" + UUID.randomUUID());
        event.setSettingsJson("{}");
        event.setCreatedAt(now);
        event.setUpdatedAt(now);

        return eventRepository.saveAndFlush(event).getId();
    }

    private UUID createDisciplineTemplate() {
        DisciplineTemplateEntity template = new DisciplineTemplateEntity();
        template.setId(UUID.randomUUID());
        template.setSportId(SWIMMING_SPORT_ID);
        template.setCode("results-50m-" + UUID.randomUUID());
        template.setName("50m Freestyle");
        template.setCompetitionFormat("INDIVIDUAL");
        template.setUnitType("HEAT");
        template.setResultType("TIME");
        template.setRankingStrategy("ASC");
        template.setDefaultMeta("""
                {
                  "distanceMeters": 50,
                  "stroke": "FREESTYLE",
                  "poolLengthMeters": 25
                }
                """);

        return disciplineTemplateRepository.saveAndFlush(template).getId();
    }

    private UUID createDiscipline(UUID eventId, UUID templateId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        EventDisciplineEntity discipline = new EventDisciplineEntity();
        discipline.setId(UUID.randomUUID());
        discipline.setEventId(eventId);
        discipline.setDisciplineTemplateId(templateId);
        discipline.setCode("50m-freestyle-men");
        discipline.setName("50m Freestyle");
        discipline.setCompetitionFormat("INDIVIDUAL");
        discipline.setUnitType("HEAT");
        discipline.setResultType("TIME");
        discipline.setRankingStrategy("ASC");
        discipline.setParticipantLimit(40);
        discipline.setEntryFeeAmount(BigDecimal.ZERO);
        discipline.setEntryFeeCurrency("RUB");
        discipline.setStatus(EventDisciplineStatus.PUBLISHED.name());
        discipline.setSettingsJson("""
                {
                  "distanceMeters": 50,
                  "stroke": "FREESTYLE",
                  "gender": "MALE"
                }
                """);
        discipline.setCreatedAt(now);
        discipline.setUpdatedAt(now);

        return eventDisciplineRepository.saveAndFlush(discipline).getId();
    }

    private PlatformUserPrincipal principal(
            UserEntity user,
            String role
    ) {
        return new PlatformUserPrincipal(
                user.getId(),
                user.getEmail(),
                "",
                user.getStatus(),
                List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
    }

    private PlatformUserPrincipal principal(String role) {
        UUID id = UUID.randomUUID();

        return new PlatformUserPrincipal(
                id,
                role.toLowerCase() + "-" + id + "@example.com",
                "",
                "active",
                List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
    }

    private String randomPhone() {
        String digits = UUID.randomUUID()
                .toString()
                .replaceAll("[^0-9]", "");

        return "+7999" + (digits + "0000000").substring(0, 7);
    }

    private record Fixture(
            UUID eventId,
            UUID disciplineId,
            UUID unitId,
            UUID entryId
    ) {
    }
}
