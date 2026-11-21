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
import com.acme.sportplatform.organizations.infrastructure.jpa.OrganizationEntity;
import com.acme.sportplatform.organizations.infrastructure.jpa.OrganizationMemberEntity;
import com.acme.sportplatform.organizations.infrastructure.jpa.OrganizationMemberRepository;
import com.acme.sportplatform.organizations.infrastructure.jpa.OrganizationRepository;
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
class ResultsIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private OrganizationMemberRepository organizationMemberRepository;

    private final Map<UUID, PlatformUserPrincipal> registrationOrganizersByEvent =
            new java.util.HashMap<>();



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

    @Test
    void shouldRecordResultAndRecalculatePlaces() throws Exception {
        Fixture fixture = createFixture();

        recordAndRecalculate(fixture, "28.11", "PENDING", "ASC")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id")
                        .value(fixture.unitId().toString()))
                .andExpect(jsonPath("$.entries.length()").value(1))
                .andExpect(jsonPath("$.entries[0].rawValue").value("28.11"))
                .andExpect(jsonPath("$.entries[0].resultStatus").value("VALID"))
                .andExpect(jsonPath("$.entries[0].finalPlace").value(1));

        ResultEntity saved = findResult(fixture);

        assertEquals("28.11", saved.getRawValue());
        assertEquals("TIME", saved.getResultType());
        assertEquals("VALID", saved.getStatus());
        assertEquals(Integer.valueOf(1), saved.getFinalPlace());
    }

    @Test
    void shouldRollbackNewResultWhenRecalculationFails() throws Exception {
        Fixture fixture = createFixture();

        recordAndRecalculate(fixture, "28.11", "PENDING", "INVALID")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("results.invalid_ranking_strategy"));

        assertTrue(
                resultRepository
                        .findByCompetitionUnitEntryId(fixture.entryId())
                        .isEmpty(),
                "Результат не должен сохраняться при ошибке пересчёта"
        );
    }

    @Test
    void shouldRollbackExistingResultUpdateWhenRecalculationFails()
            throws Exception {

        Fixture fixture = createFixture();

        recordAndRecalculate(fixture, "28.11", "PENDING", "ASC")
                .andExpect(status().isOk());

        ResultEntity before = findResult(fixture);

        UUID originalId = before.getId();
        Long originalVersion = before.getVersion();
        OffsetDateTime originalUpdatedAt = before.getUpdatedAt();
        UUID originalRecordedBy = before.getRecordedByUserId();

        recordAndRecalculate(fixture, "26.50", "PENDING", "INVALID")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("results.invalid_ranking_strategy"));

        ResultEntity after = findResult(fixture);

        assertEquals(originalId, after.getId());
        assertEquals("28.11", after.getRawValue());
        assertEquals("TIME", after.getResultType());
        assertEquals("VALID", after.getStatus());
        assertEquals(Integer.valueOf(1), after.getFinalPlace());
        assertEquals(originalVersion, after.getVersion());
        assertEquals(originalUpdatedAt, after.getUpdatedAt());
        assertEquals(originalRecordedBy, after.getRecordedByUserId());
    }

    @Test
    void shouldAcceptShortAndFullSpecialStatuses() throws Exception {
        Fixture fixture = createFixture();

        Map<String, String> statuses = Map.of(
                "DNS", "DID_NOT_START",
                "DNF", "DID_NOT_FINISH",
                "DSQ", "DISQUALIFIED",
                "DID_NOT_START", "DID_NOT_START",
                "DID_NOT_FINISH", "DID_NOT_FINISH",
                "DISQUALIFIED", "DISQUALIFIED"
        );

        for (Map.Entry<String, String> statusEntry : statuses.entrySet()) {
            recordOnly(fixture, "28.11", statusEntry.getKey())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status")
                            .value(statusEntry.getValue()))
                    .andExpect(jsonPath("$.rawValue").doesNotExist())
                    .andExpect(jsonPath("$.finalPlace").doesNotExist());

            ResultEntity saved = findResult(fixture);

            assertEquals(statusEntry.getValue(), saved.getStatus());
            assertNull(saved.getRawValue());
            assertNull(saved.getFinalPlace());
        }
    }

    @Test
    void shouldRejectDirectValidStatus() throws Exception {
        Fixture fixture = createFixture();

        recordOnly(fixture, "28.11", "VALID")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("results.status_not_allowed"));

        assertTrue(
                resultRepository
                        .findByCompetitionUnitEntryId(fixture.entryId())
                        .isEmpty()
        );
    }

    @Test
    void shouldRejectWrongEventIdOnBothPublicRoutes() throws Exception {
        Fixture fixture = createFixture();

        recordAndRecalculate(fixture, "28.11", "PENDING", "ASC")
                .andExpect(status().isOk());

        publish(fixture)
                .andExpect(status().isNoContent());

        List<String> routes = List.of(
                "/api/v1/public/events/{eventId}/disciplines/{disciplineId}/results",
                "/api/v1/events/{eventId}/disciplines/{disciplineId}/results"
        );

        UUID wrongEventId = createEvent();
        PlatformUserPrincipal operator = operatorFor(fixture);

        for (String route : routes) {
            mockMvc.perform(get(
                            route,
                            wrongEventId,
                            fixture.disciplineId()
                    )
                            .with(user(operator)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code")
                            .value("results.public_not_found"));

            mockMvc.perform(get(
                            route,
                            fixture.eventId(),
                            fixture.disciplineId()
                    )
                            .with(user(operator)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.eventId")
                            .value(fixture.eventId().toString()));
        }
    }

    @Test
    void shouldRejectChangesToPublishedUnit() throws Exception {
        Fixture fixture = createFixture();

        recordAndRecalculate(fixture, "28.11", "PENDING", "ASC")
                .andExpect(status().isOk());

        publish(fixture)
                .andExpect(status().isNoContent());

        recordOnly(fixture, "26.50", "PENDING")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("results.unit_published"));

        recordAndRecalculate(fixture, "26.50", "PENDING", "ASC")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("results.unit_published"));

        mockMvc.perform(post(
                        "/api/v1/competition-units/{unitId}/recalculate",
                        fixture.unitId()
                )
                        .with(user(operatorFor(fixture)))
                        .param("rankingStrategy", "ASC"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("results.unit_published"));

        ResultEntity saved = findResult(fixture);

        assertEquals("28.11", saved.getRawValue());
        assertEquals("VALID", saved.getStatus());
        assertEquals(Integer.valueOf(1), saved.getFinalPlace());
    }

    @Test
    void shouldRejectPublicationWithoutResults() throws Exception {
        Fixture fixture = createFixture();

        publish(fixture)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("results.unit_has_missing_results"));

        assertEquals(
                "DRAFT",
                competitionUnitRepository.findById(fixture.unitId())
                        .orElseThrow()
                        .getStatus()
        );
    }

    @Test
    void shouldRejectPublicationBeforeRecalculation() throws Exception {
        Fixture fixture = createFixture();

        recordOnly(fixture, "28.11", "PENDING")
                .andExpect(status().isOk());

        publish(fixture)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("results.unit_has_unvalidated_results"));

        assertEquals(
                "DRAFT",
                competitionUnitRepository.findById(fixture.unitId())
                        .orElseThrow()
                        .getStatus()
        );
    }

    @Test
    void shouldReturnAnonymousStandingsFromPublishedUnitsOnly()
            throws Exception {

        Fixture firstHeat = createFixture();

        Fixture secondHeat = createAdditionalHeat(
                firstHeat.eventId(),
                firstHeat.disciplineId(),
                2
        );

        Fixture draftHeat = createAdditionalHeat(
                firstHeat.eventId(),
                firstHeat.disciplineId(),
                3
        );

        recordAndRecalculate(firstHeat, "30.00", "PENDING", "ASC")
                .andExpect(status().isOk());

        recordAndRecalculate(secondHeat, "28.50", "PENDING", "ASC")
                .andExpect(status().isOk());

        recordAndRecalculate(draftHeat, "20.00", "PENDING", "ASC")
                .andExpect(status().isOk());

        publish(firstHeat)
                .andExpect(status().isNoContent());

        publish(secondHeat)
                .andExpect(status().isNoContent());

        ResultEntity firstBefore = findResult(firstHeat);
        ResultEntity secondBefore = findResult(secondHeat);
        ResultEntity draftBefore = findResult(draftHeat);

        Long firstVersion = firstBefore.getVersion();
        Long secondVersion = secondBefore.getVersion();
        Long draftVersion = draftBefore.getVersion();

        OffsetDateTime firstUpdatedAt = firstBefore.getUpdatedAt();
        OffsetDateTime secondUpdatedAt = secondBefore.getUpdatedAt();
        OffsetDateTime draftUpdatedAt = draftBefore.getUpdatedAt();

        mockMvc.perform(get(
                        "/api/v1/public/events/{eventId}"
                                + "/disciplines/{disciplineId}/standings",
                        firstHeat.eventId(),
                        firstHeat.disciplineId()
                )
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId")
                        .value(firstHeat.eventId().toString()))
                .andExpect(jsonPath("$.eventDisciplineId")
                        .value(firstHeat.disciplineId().toString()))
                .andExpect(jsonPath("$.resultType").value("TIME"))
                .andExpect(jsonPath("$.rankingStrategy").value("ASC"))
                .andExpect(jsonPath("$.tiePolicy").value("COMPETITION"))
                .andExpect(jsonPath("$.entries.length()").value(2))
                .andExpect(jsonPath("$.entries[0].competitionUnitId")
                        .value(secondHeat.unitId().toString()))
                .andExpect(jsonPath("$.entries[0].rawValue").value("28.50"))
                .andExpect(jsonPath("$.entries[0].overallPlace").value(1))
                .andExpect(jsonPath("$.entries[0].unitPlace").value(1))
                .andExpect(jsonPath("$.entries[0].status").value("VALID"))
                .andExpect(jsonPath("$.entries[1].competitionUnitId")
                        .value(firstHeat.unitId().toString()))
                .andExpect(jsonPath("$.entries[1].rawValue").value("30.00"))
                .andExpect(jsonPath("$.entries[1].overallPlace").value(2))
                .andExpect(jsonPath("$.entries[1].unitPlace").value(1))
                .andExpect(jsonPath("$.entries[1].status").value("VALID"));

        assertResultUnchanged(
                firstHeat,
                "30.00",
                firstVersion,
                firstUpdatedAt
        );

        assertResultUnchanged(
                secondHeat,
                "28.50",
                secondVersion,
                secondUpdatedAt
        );

        assertResultUnchanged(
                draftHeat,
                "20.00",
                draftVersion,
                draftUpdatedAt
        );

        assertEquals(
                "DRAFT",
                competitionUnitRepository.findById(draftHeat.unitId())
                        .orElseThrow()
                        .getStatus()
        );
    }

    @Test
    void shouldRejectWrongEventIdForAnonymousStandings()
            throws Exception {

        Fixture fixture = createFixture();

        recordAndRecalculate(fixture, "28.11", "PENDING", "ASC")
                .andExpect(status().isOk());

        publish(fixture)
                .andExpect(status().isNoContent());

        UUID wrongEventId = createEvent();

        mockMvc.perform(get(
                        "/api/v1/public/events/{eventId}"
                                + "/disciplines/{disciplineId}/standings",
                        wrongEventId,
                        fixture.disciplineId()
                )
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code")
                        .value("results.public_not_found"));

        mockMvc.perform(get(
                        "/api/v1/public/events/{eventId}"
                                + "/disciplines/{disciplineId}/standings",
                        fixture.eventId(),
                        fixture.disciplineId()
                )
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(1));
    }

    @Test
    void shouldReturnEmptyAnonymousStandingsUntilUnitIsPublished()
            throws Exception {

        Fixture fixture = createFixture();

        recordAndRecalculate(fixture, "28.11", "PENDING", "ASC")
                .andExpect(status().isOk());

        mockMvc.perform(get(
                        "/api/v1/public/events/{eventId}"
                                + "/disciplines/{disciplineId}/standings",
                        fixture.eventId(),
                        fixture.disciplineId()
                )
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId")
                        .value(fixture.eventId().toString()))
                .andExpect(jsonPath("$.eventDisciplineId")
                        .value(fixture.disciplineId().toString()))
                .andExpect(jsonPath("$.entries.length()").value(0));

        publish(fixture)
                .andExpect(status().isNoContent());

        mockMvc.perform(get(
                        "/api/v1/public/events/{eventId}"
                                + "/disciplines/{disciplineId}/standings",
                        fixture.eventId(),
                        fixture.disciplineId()
                )
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(1))
                .andExpect(jsonPath("$.entries[0].overallPlace").value(1))
                .andExpect(jsonPath("$.entries[0].rawValue").value("28.11"));
    }

    @Test
    void shouldRejectOperatorAssignedToAnotherEvent() throws Exception {
        Fixture ownEvent = createFixture();
        Fixture foreignEvent = createFixture();

        PlatformUserPrincipal operator = operatorFor(ownEvent);

        mockMvc.perform(get(
                        "/api/v1/competition-units/{unitId}",
                        ownEvent.unitId()
                )
                        .with(user(operator)))
                .andExpect(status().isOk());

        mockMvc.perform(get(
                        "/api/v1/competition-units/{unitId}",
                        foreignEvent.unitId()
                )
                        .with(user(operator)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(
                        "/api/v1/event-disciplines/{disciplineId}/competition-units",
                        foreignEvent.disciplineId()
                )
                        .with(user(operator)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/results")
                        .with(user(operator))
                        .param("resultType", "TIME")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resultJson(
                                foreignEvent.entryId(),
                                "28.11",
                                "PENDING"
                        )))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(
                        "/api/v1/competition-units/{unitId}/results",
                        foreignEvent.unitId()
                )
                        .with(user(operator))
                        .param("resultType", "TIME")
                        .param("rankingStrategy", "ASC")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resultJson(
                                foreignEvent.entryId(),
                                "28.11",
                                "PENDING"
                        )))
                .andExpect(status().isForbidden());

        // Свой unitId в URL не должен разрешать запись чужой entry.
        mockMvc.perform(post(
                        "/api/v1/competition-units/{unitId}/results",
                        ownEvent.unitId()
                )
                        .with(user(operator))
                        .param("resultType", "TIME")
                        .param("rankingStrategy", "ASC")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resultJson(
                                foreignEvent.entryId(),
                                "28.11",
                                "PENDING"
                        )))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(
                        "/api/v1/competition-units/{unitId}/recalculate",
                        foreignEvent.unitId()
                )
                        .with(user(operator))
                        .param("rankingStrategy", "ASC"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(
                        "/api/v1/competition-units/{unitId}/publication",
                        foreignEvent.unitId()
                )
                        .with(user(operator))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "published": true
                                }
                                """))
                .andExpect(status().isForbidden());

        assertTrue(
                resultRepository
                        .findByCompetitionUnitEntryId(foreignEvent.entryId())
                        .isEmpty()
        );

        assertEquals(
                "DRAFT",
                competitionUnitRepository.findById(foreignEvent.unitId())
                        .orElseThrow()
                        .getStatus()
        );
    }

    @Test
    void shouldRejectResultWriteAfterOperatorAssignmentIsRemoved()
            throws Exception {

        Fixture fixture = createFixture();
        PlatformUserPrincipal operator = operatorFor(fixture);

        recordAndRecalculate(fixture, "28.11", "PENDING", "ASC")
                .andExpect(status().isOk());

        ResultEntity before = findResult(fixture);
        Long originalVersion = before.getVersion();

        new TransactionTemplate(transactionManager)
                .executeWithoutResult(transactionStatus ->
                        operatorAssignmentRepository.deleteAssignment(
                                fixture.eventId(),
                                operator.getUserId()
                        )
                );

        mockMvc.perform(post("/api/v1/results")
                        .with(user(operator))
                        .param("resultType", "TIME")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resultJson(
                                fixture.entryId(),
                                "26.50",
                                "PENDING"
                        )))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(
                        "/api/v1/competition-units/{unitId}",
                        fixture.unitId()
                )
                        .with(user(operator)))
                .andExpect(status().isForbidden());

        ResultEntity after = findResult(fixture);

        assertEquals("28.11", after.getRawValue());
        assertEquals("VALID", after.getStatus());
        assertEquals(Integer.valueOf(1), after.getFinalPlace());
        assertEquals(originalVersion, after.getVersion());
    }

    @Test
    void shouldAllowPlatformAdminToWriteWithoutEventAssignment()
            throws Exception {

        Fixture fixture = createFixture();

        UserEntity admin = createUser("results-admin");
        grantGlobalRole(admin.getId(), "PLATFORM_ADMIN");

        PlatformUserPrincipal adminPrincipal =
                principal(admin, "PLATFORM_ADMIN");

        mockMvc.perform(post(
                        "/api/v1/competition-units/{unitId}/results",
                        fixture.unitId()
                )
                        .with(user(adminPrincipal))
                        .param("resultType", "TIME")
                        .param("rankingStrategy", "ASC")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resultJson(
                                fixture.entryId(),
                                "28.11",
                                "PENDING"
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries[0].resultStatus").value("VALID"))
                .andExpect(jsonPath("$.entries[0].finalPlace").value(1));

        assertTrue(
                operatorAssignmentRepository
                        .findByEventIdOrderByCreatedAtAscIdAsc(fixture.eventId())
                        .isEmpty()
        );
    }

    @Test
    void shouldRejectResultTypeDifferentFromDiscipline() throws Exception {
        Fixture fixture = createFixture();

        mockMvc.perform(post("/api/v1/results")
                        .with(user(operatorFor(fixture)))
                        .param("resultType", "POINTS")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resultJson(
                                fixture.entryId(),
                                "100",
                                "PENDING"
                        )))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("results.result_type_mismatch"));

        assertTrue(
                resultRepository
                        .findByCompetitionUnitEntryId(fixture.entryId())
                        .isEmpty(),
                "Результат не должен сохраняться при несовпадении resultType"
        );
    }

    @Test
    void shouldRejectRankingStrategyDifferentFromDiscipline()
            throws Exception {

        Fixture fixture = createFixture();

        recordAndRecalculate(fixture, "28.11", "PENDING", "ASC")
                .andExpect(status().isOk());

        ResultEntity before = findResult(fixture);

        UUID originalId = before.getId();
        Long originalVersion = before.getVersion();
        OffsetDateTime originalUpdatedAt = before.getUpdatedAt();
        UUID originalRecordedBy = before.getRecordedByUserId();

        mockMvc.perform(post(
                        "/api/v1/competition-units/{unitId}/recalculate",
                        fixture.unitId()
                )
                        .with(user(operatorFor(fixture)))
                        .param("rankingStrategy", "DESC"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("results.ranking_strategy_mismatch"));

        assertResultUnchanged(
                fixture,
                "28.11",
                originalVersion,
                originalUpdatedAt
        );

        ResultEntity after = findResult(fixture);

        assertEquals(originalId, after.getId());
        assertEquals(originalRecordedBy, after.getRecordedByUserId());
    }

    @Test
    void shouldRollbackResultUpdateWhenRankingDoesNotMatchDiscipline()
            throws Exception {

        Fixture fixture = createFixture();

        recordAndRecalculate(fixture, "28.11", "PENDING", "ASC")
                .andExpect(status().isOk());

        ResultEntity before = findResult(fixture);

        UUID originalId = before.getId();
        Long originalVersion = before.getVersion();
        OffsetDateTime originalUpdatedAt = before.getUpdatedAt();
        UUID originalRecordedBy = before.getRecordedByUserId();

        recordAndRecalculate(fixture, "26.50", "PENDING", "DESC")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("results.ranking_strategy_mismatch"));

        assertResultUnchanged(
                fixture,
                "28.11",
                originalVersion,
                originalUpdatedAt
        );

        ResultEntity after = findResult(fixture);

        assertEquals(originalId, after.getId());
        assertEquals(originalRecordedBy, after.getRecordedByUserId());
    }

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate rankingJdbc;

    @Autowired
    private com.acme.sportplatform.results.application.RecalculatePlacesUseCase sqlRecalculate;

    @Autowired
    private com.acme.sportplatform.results.application.AssignRegistrationToUnitUseCase assignToUnit;

    @Autowired
    private com.acme.sportplatform.results.application.PublishCompetitionUnitUseCase publishUnit;

    @Test
    void shouldRejectAssignmentToPublishedUnitWithoutCreatingEntry()
            throws Exception {

        Fixture fixture = createFixture();

        UUID registrationId = createConfirmedRegistration(
                fixture.disciplineId()
        );

        recordAndRecalculate(fixture, "28.11", "PENDING", "ASC")
                .andExpect(status().isOk());

        publish(fixture)
                .andExpect(status().isNoContent());

        UserEntity admin = createUser("assignment-admin");
        grantGlobalRole(admin.getId(), "PLATFORM_ADMIN");

        PlatformUserPrincipal adminPrincipal =
                principal(admin, "PLATFORM_ADMIN");

        mockMvc.perform(post(
                        "/api/v1/competition-units/{unitId}/entries",
                        fixture.unitId()
                )
                        .with(user(adminPrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "registrationId", registrationId,
                                "laneOrPosition", 2
                        ))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("results.unit_published"));

        assertEquals(
                1,
                entryRepository
                        .findByCompetitionUnitId(fixture.unitId())
                        .size()
        );

        assertTrue(
                !entryRepository
                        .existsByCompetitionUnitIdAndRegistrationId(
                                fixture.unitId(),
                                registrationId
                        ),
                "Участник не должен добавляться в опубликованный заплыв"
        );

        assertEquals(
                "PUBLISHED",
                competitionUnitRepository
                        .findById(fixture.unitId())
                        .orElseThrow()
                        .getStatus()
        );
    }

    @Test
    void shouldAssignSharedPlacesToEquivalentNumericValuesAscending() throws Exception {
        verifySqlSharedPlaces("ASC");
    }

    @Test
    void shouldAssignSharedPlacesToEquivalentNumericValuesDescending() throws Exception {
        verifySqlSharedPlaces("DESC");
    }

    @Test
    void shouldLeaveSpecialResultsCompletelyUnchangedDuringSqlRecalculation()
            throws Exception {
        Fixture fixture = createFixture();
        recordOnly(fixture, "28.11", "PENDING").andExpect(status().isOk());
        Fixture dns = additionalEntry(fixture, 2);
        Fixture dnf = additionalEntry(fixture, 3);
        Fixture dsq = additionalEntry(fixture, 4);
        recordOnly(dns, "ignored", "DNS").andExpect(status().isOk());
        recordOnly(dnf, "ignored", "DNF").andExpect(status().isOk());
        recordOnly(dsq, "ignored", "DSQ").andExpect(status().isOk());

        Map<UUID, List<Map<String, Object>>> before = new HashMap<>();
        for (Fixture special : List.of(dns, dnf, dsq)) {
            before.put(special.entryId(), entryResultRows(special.entryId()));
        }

        sqlRecalculate.execute(fixture.unitId(), "ASC");

        assertEquals("VALID", findResult(fixture).getStatus());
        assertEquals(Integer.valueOf(1), findResult(fixture).getFinalPlace());
        for (Fixture special : List.of(dns, dnf, dsq)) {
            assertEquals(before.get(special.entryId()), entryResultRows(special.entryId()));
        }
    }

    @Test
    void shouldRejectMalformedValueWithoutUpdatingAnyResult() throws Exception {
        Fixture fixture = createFixture();
        Fixture invalid = additionalEntry(fixture, 2);
        recordOnly(fixture, "28.11", "PENDING").andExpect(status().isOk());
        recordOnly(invalid, "29.00", "PENDING").andExpect(status().isOk());
        // Simulate pre-fix stored data. New API writes must reject this value.
        rankingJdbc.update("UPDATE results SET raw_value='01:60' WHERE competition_unit_entry_id=?",
                invalid.entryId());
        List<Map<String, Object>> before = unitResultRows(fixture.unitId());

        mockMvc.perform(post("/api/v1/competition-units/{unitId}/recalculate", fixture.unitId())
                        .with(user(operatorFor(fixture)))
                        .param("rankingStrategy", "ASC"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("results.invalid_raw_value"));

        assertEquals(before, unitResultRows(fixture.unitId()));
    }

    @Test
    void shouldKeepPlacesAndIncrementVersionOncePerStandaloneRecalculation()
            throws Exception {
        Fixture fixture = createFixture();
        recordOnly(fixture, "28.11", "PENDING").andExpect(status().isOk());
        ResultEntity before = findResult(fixture);
        long originalVersion = before.getVersion();
        OffsetDateTime createdAt = before.getCreatedAt();
        UUID recordedBy = before.getRecordedByUserId();

        sqlRecalculate.execute(fixture.unitId(), "ASC");
        ResultEntity first = findResult(fixture);
        assertEquals(Long.valueOf(originalVersion + 1), first.getVersion());
        assertEquals(Integer.valueOf(1), first.getFinalPlace());
        assertEquals("VALID", first.getStatus());
        assertEquals(createdAt, first.getCreatedAt());
        assertEquals(recordedBy, first.getRecordedByUserId());

        sqlRecalculate.execute(fixture.unitId(), "ASC");
        ResultEntity second = findResult(fixture);
        assertEquals(Long.valueOf(originalVersion + 2), second.getVersion());
        assertEquals(Integer.valueOf(1), second.getFinalPlace());
        assertEquals("VALID", second.getStatus());
        assertEquals("28.11", second.getRawValue());
        assertEquals(createdAt, second.getCreatedAt());
        assertEquals(recordedBy, second.getRecordedByUserId());
    }

    @Test
    void shouldApplyResearchNumericContractInPostgres() {
        List<NumericExample> examples = List.of(
                new NumericExample(null, null),
                new NumericExample("", null),
                new NumericExample(" ", null),
                new NumericExample("NaN", null),
                new NumericExample("Infinity", null),
                new NumericExample("-Infinity", null),
                new NumericExample("1e3", null),
                new NumericExample("0x1.0p0", null),
                new NumericExample("1D", null),
                new NumericExample(".5", null),
                new NumericExample("1.", null),
                new NumericExample("12,5", null),
                new NumericExample("12 ", null),
                new NumericExample("12\n", null),
                new NumericExample("1:60", null),
                new NumericExample("1:-2", null),
                new NumericExample("1.5:20", null),
                new NumericExample("1:2:3", null),
                new NumericExample("1:", null),
                new NumericExample(":12", null),
                new NumericExample("9".repeat(51), null),
                new NumericExample("-1", -1d),
                new NumericExample("+1.25", 1.25d),
                new NumericExample("0", 0d),
                new NumericExample("-0.00", 0d),
                new NumericExample("58.42", 58.42d),
                new NumericExample("00:58.42", 58.42d),
                new NumericExample("1:00", 60d),
                new NumericExample("01:02.34", 62.34d),
                new NumericExample("0:0.1", 0.1d)
        );
        for (NumericExample example : examples) {
            Double actual = rankingJdbc.queryForObject(
                    "SELECT results_numeric_value(?)",
                    Double.class,
                    example.raw()
            );
            assertEquals(example.expected(), actual, "rawValue=" + example.raw());
        }
    }

    @Test
    void shouldRollbackAllSqlUpdatesWhenDatabaseTriggerFails() throws Exception {
        Fixture fixture = createFixture();
        Fixture second = additionalEntry(fixture, 2);
        recordOnly(fixture, "30.00", "PENDING").andExpect(status().isOk());
        recordOnly(second, "28.00", "PENDING").andExpect(status().isOk());
        List<Map<String, Object>> before = unitResultRows(fixture.unitId());
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String function = "test_rank_failure_" + suffix;
        String trigger = "test_rank_trigger_" + suffix;
        rankingJdbc.execute("""
                CREATE FUNCTION %s() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    IF NEW.competition_unit_entry_id = '%s'::uuid
                       AND NEW.status = 'VALID' THEN
                        RAISE EXCEPTION 'injected ranking test failure';
                    END IF;
                    RETURN NEW;
                END;
                $$
                """.formatted(function, second.entryId()));
        try {
            rankingJdbc.execute("CREATE TRIGGER " + trigger
                    + " BEFORE UPDATE ON results FOR EACH ROW EXECUTE FUNCTION "
                    + function + "()");
            org.junit.jupiter.api.Assertions.assertThrows(
                    org.springframework.dao.DataAccessException.class,
                    () -> sqlRecalculate.execute(fixture.unitId(), "ASC")
            );
            assertEquals(before, unitResultRows(fixture.unitId()));
        } finally {
            rankingJdbc.execute("DROP TRIGGER IF EXISTS " + trigger + " ON results");
            rankingJdbc.execute("DROP FUNCTION IF EXISTS " + function + "()");
        }
    }

    @Test
    void shouldSerializeTwoAssignmentsToTheSameLane() throws Exception {
        Fixture fixture = createFixture();
        UUID firstRegistration = createConfirmedRegistration(fixture.disciplineId());
        UUID secondRegistration = createConfirmedRegistration(fixture.disciplineId());
        java.util.concurrent.atomic.AtomicReference<UUID> firstEntry =
                new java.util.concurrent.atomic.AtomicReference<>();

        java.util.concurrent.ExecutionException failure =
                org.junit.jupiter.api.Assertions.assertThrows(
                        java.util.concurrent.ExecutionException.class,
                        () -> withBlockedUnitWorker(
                                fixture.unitId(),
                                () -> firstEntry.set(assignToUnit.execute(
                                        fixture.unitId(), firstRegistration, 2)),
                                () -> assignToUnit.execute(
                                        fixture.unitId(), secondRegistration, 2),
                                () -> { }
                        )
                );

        org.junit.jupiter.api.Assertions.assertInstanceOf(
                com.acme.sportplatform.common.exception.BusinessException.class,
                failure.getCause()
        );
        org.junit.jupiter.api.Assertions.assertNotNull(firstEntry.get());
        assertTrue(entryRepository.existsByCompetitionUnitIdAndRegistrationId(
                fixture.unitId(), firstRegistration));
        assertTrue(!entryRepository.existsByCompetitionUnitIdAndRegistrationId(
                fixture.unitId(), secondRegistration));
        long assignedToLane = entryRepository.findByCompetitionUnitId(fixture.unitId())
                .stream()
                .filter(entry -> Integer.valueOf(2).equals(entry.getLaneOrPosition()))
                .count();
        assertEquals(1L, assignedToLane);
        assertEquals(2, entryRepository.findByCompetitionUnitId(fixture.unitId()).size());
    }

    @Test
    void shouldRejectRecalculationWhenPublicationCommitsBeforeUnitLock()
            throws Exception {
        Fixture fixture = createFixture();
        recordAndRecalculate(fixture, "28.11", "PENDING", "ASC")
                .andExpect(status().isOk());
        List<Map<String, Object>> before = unitResultRows(fixture.unitId());

        java.util.concurrent.ExecutionException failure =
                org.junit.jupiter.api.Assertions.assertThrows(
                        java.util.concurrent.ExecutionException.class,
                        () -> withBlockedUnitWorker(
                                fixture.unitId(),
                                () -> { },
                                () -> {
                                    sqlRecalculate.execute(fixture.unitId(), "ASC");
                                    return Boolean.TRUE;
                                },
                                () -> publishUnit.execute(fixture.unitId(), true)
                        )
                );

        org.junit.jupiter.api.Assertions.assertInstanceOf(
                com.acme.sportplatform.common.exception.BusinessException.class,
                failure.getCause()
        );
        assertEquals("PUBLISHED", competitionUnitRepository
                .findById(fixture.unitId()).orElseThrow().getStatus());
        assertEquals(before, unitResultRows(fixture.unitId()));
    }

    @Test
    void shouldRankCommittedResultEditAfterWaitingForUnitLock() throws Exception {
        Fixture fixture = createFixture();
        Fixture second = additionalEntry(fixture, 2);
        recordOnly(fixture, "50", "PENDING").andExpect(status().isOk());
        recordOnly(second, "60", "PENDING").andExpect(status().isOk());
        long firstVersion = findResult(fixture).getVersion();
        long secondVersion = findResult(second).getVersion();

        withBlockedUnitWorker(
                fixture.unitId(),
                () -> { },
                () -> {
                    sqlRecalculate.execute(fixture.unitId(), "ASC");
                    return Boolean.TRUE;
                },
                () -> rankingJdbc.update(
                        """
                        UPDATE results
                        SET raw_value = '70', version = version + 1
                        WHERE competition_unit_entry_id = ?
                        """,
                        fixture.entryId()
                )
        );

        ResultEntity firstAfter = findResult(fixture);
        ResultEntity secondAfter = findResult(second);
        assertEquals("70", firstAfter.getRawValue());
        assertEquals("VALID", firstAfter.getStatus());
        assertEquals(Integer.valueOf(2), firstAfter.getFinalPlace());
        assertEquals(Long.valueOf(firstVersion + 2), firstAfter.getVersion());
        assertEquals("60", secondAfter.getRawValue());
        assertEquals("VALID", secondAfter.getStatus());
        assertEquals(Integer.valueOf(1), secondAfter.getFinalPlace());
        assertEquals(Long.valueOf(secondVersion + 1), secondAfter.getVersion());
    }

    @Test
    void shouldAllowRecalculationWithoutResultsAndWithNoEntries() throws Exception {
        Fixture fixture = createFixture();
        sqlRecalculate.execute(fixture.unitId(), "ASC");
        assertTrue(unitResultRows(fixture.unitId()).isEmpty());
        assertEquals(1, entryRepository.findByCompetitionUnitId(fixture.unitId()).size());

        new TransactionTemplate(transactionManager).executeWithoutResult(tx ->
                entryRepository.deleteById(fixture.entryId()));
        sqlRecalculate.execute(fixture.unitId(), "ASC");
        assertTrue(unitResultRows(fixture.unitId()).isEmpty());
        assertTrue(entryRepository.findByCompetitionUnitId(fixture.unitId()).isEmpty());
    }

        private void verifySqlSharedPlaces(String strategy) throws Exception {
                Fixture first = createFixture();
                Fixture second = additionalEntry(first, 2);
                Fixture third = additionalEntry(first, 3);
                Fixture fourth = additionalEntry(first, 4);

                rankingJdbc.update(
                        """
                        UPDATE event_disciplines
                        SET ranking_strategy = ?
                        WHERE id = ?
                        """,
                        strategy,
                        first.disciplineId()
                );

                recordOnly(first, "58.42", "PENDING")
                        .andExpect(status().isOk());

                recordOnly(second, "00:58.42", "PENDING")
                        .andExpect(status().isOk());

                recordOnly(third, "60", "PENDING")
                        .andExpect(status().isOk());

                recordOnly(fourth, "50", "PENDING")
                        .andExpect(status().isOk());

                sqlRecalculate.execute(first.unitId(), strategy);

                ResultEntity firstAfter = findResult(first);
                ResultEntity secondAfter = findResult(second);
                ResultEntity thirdAfter = findResult(third);
                ResultEntity fourthAfter = findResult(fourth);

                assertEquals(
                        Integer.valueOf(2),
                        firstAfter.getFinalPlace(),
                        "Первый результат 58.42 должен занимать общее второе место"
                );

                assertEquals(
                        Integer.valueOf(2),
                        secondAfter.getFinalPlace(),
                        "Эквивалентный результат 00:58.42 должен занимать то же место"
                );

                boolean ascending = "ASC".equals(strategy);

                assertEquals(
                        Integer.valueOf(ascending ? 4 : 1),
                        thirdAfter.getFinalPlace(),
                        "Результат 60 должен учитывать направление ранжирования "
                                + "и пропуск места после равенства"
                );

                assertEquals(
                        Integer.valueOf(ascending ? 1 : 4),
                        fourthAfter.getFinalPlace(),
                        "Результат 50 должен учитывать направление ранжирования "
                                + "и пропуск места после равенства"
                );

                for (Fixture fixture : List.of(first, second, third, fourth)) {
                        assertEquals(
                                "VALID",
                                findResult(fixture).getStatus(),
                                "После SQL-пересчёта результат должен быть учтён"
                        );
                }

                assertEquals("58.42", firstAfter.getRawValue());
                assertEquals("00:58.42", secondAfter.getRawValue());
                assertEquals("60", thirdAfter.getRawValue());
                assertEquals("50", fourthAfter.getRawValue());
                }

    private Fixture additionalEntry(Fixture parent, int lane) throws Exception {
        UUID registrationId = createConfirmedRegistration(parent.disciplineId());
        CompetitionUnitEntryEntity entry = new CompetitionUnitEntryEntity();
        entry.setId(UUID.randomUUID());
        entry.setCompetitionUnitId(parent.unitId());
        entry.setRegistrationId(registrationId);
        entry.setLaneOrPosition(lane);
        entry.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        UUID entryId = entryRepository.saveAndFlush(entry).getId();
        return new Fixture(parent.eventId(), parent.disciplineId(), parent.unitId(), entryId);
    }

    private List<Map<String, Object>> unitResultRows(UUID unitId) {
        return rankingJdbc.queryForList("""
                SELECT r.*
                FROM results r
                JOIN competition_unit_entries e ON e.id = r.competition_unit_entry_id
                WHERE e.competition_unit_id = ?
                ORDER BY r.id::text COLLATE "C"
                """, unitId);
    }

    private List<Map<String, Object>> entryResultRows(UUID entryId) {
        return rankingJdbc.queryForList(
                "SELECT * FROM results WHERE competition_unit_entry_id = ?", entryId);
    }

    private <T> T withBlockedUnitWorker(
            UUID unitId,
            Runnable beforeWorker,
            java.util.function.Supplier<T> worker,
            Runnable whileBlocked
    ) throws Exception {
        assertTrue(!org.springframework.transaction.support.TransactionSynchronizationManager
                .isActualTransactionActive(),
                "Concurrency checks must run without an outer test transaction");
        java.util.concurrent.ExecutorService pool =
                java.util.concurrent.Executors.newSingleThreadExecutor();
        java.util.concurrent.atomic.AtomicReference<java.util.concurrent.Future<T>> future =
                new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicInteger workerPid =
                new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.CountDownLatch ready = new java.util.concurrent.CountDownLatch(1);
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
                Integer holderPid = rankingJdbc.queryForObject("SELECT pg_backend_pid()", Integer.class);
                rankingJdbc.queryForObject(
                        "SELECT id FROM competition_units WHERE id = ? FOR UPDATE",
                        UUID.class, unitId);
                beforeWorker.run();
                future.set(pool.submit(() -> new TransactionTemplate(transactionManager)
                        .execute(workerTx -> {
                            rankingJdbc.execute("SET LOCAL lock_timeout = '12s'");
                            rankingJdbc.execute("SET LOCAL statement_timeout = '15s'");
                            workerPid.set(rankingJdbc.queryForObject(
                                    "SELECT pg_backend_pid()", Integer.class));
                            ready.countDown();
                            return worker.get();
                        })));
                try {
                    assertTrue(ready.await(8, java.util.concurrent.TimeUnit.SECONDS),
                            "Worker did not obtain a database connection");
                    awaitUnitBlock(holderPid, workerPid.get());
                    whileBlocked.run();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                }
            });
            return future.get().get(20, java.util.concurrent.TimeUnit.SECONDS);
        } finally {
            java.util.concurrent.Future<T> submitted = future.get();
            if (submitted != null && !submitted.isDone()) {
                submitted.cancel(true);
            }
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(20, java.util.concurrent.TimeUnit.SECONDS),
                    "Database worker did not finish");
        }
    }

    private void awaitUnitBlock(int holderPid, int workerPid) throws InterruptedException {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(8);
        while (System.nanoTime() < deadline) {
            Boolean blocked = rankingJdbc.queryForObject(
                    "SELECT ? = ANY(pg_blocking_pids(?))",
                    Boolean.class, holderPid, workerPid);
            if (Boolean.TRUE.equals(blocked)) {
                return;
            }
            Thread.sleep(20);
        }
        org.junit.jupiter.api.Assertions.fail(
                "Worker " + workerPid + " did not wait for holder " + holderPid);
    }

    private record NumericExample(String raw, Double expected) {
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

        // Historical pool fixture for result algorithms; NF-01 tests exercise real admission.
        com.acme.sportplatform.support.ConfirmedRegistrationFixtures.confirmHistoricalInput(
                rankingJdbc, UUID.fromString(registrationId),
                registrationOrganizerForDiscipline(disciplineId).getUserId());

        return UUID.fromString(registrationId);
    }

    @Autowired
    private com.acme.sportplatform.organizations.infrastructure.jpa.VenueRepository
            timezoneVenueRepository;

    private EventEntity publicTimezoneEvent(String timezone) {
        var venue = new com.acme.sportplatform.organizations.infrastructure.jpa.VenueEntity();
        venue.setName("Timezone test venue " + UUID.randomUUID());
        venue.setTimezone(timezone);
        venue.setVenueMeta("{}");
        venue = timezoneVenueRepository.saveAndFlush(venue);

        var event = eventRepository.findById(createEvent()).orElseThrow();
        event.setVenueId(venue.getId());
        event.setEventStartAt(OffsetDateTime.parse("2026-11-21T23:30:00Z"));
        event.setEventEndAt(OffsetDateTime.parse("2026-11-22T01:30:00Z"));
        return eventRepository.saveAndFlush(event);
    }

    private com.fasterxml.jackson.databind.JsonNode publicTimezoneList() throws Exception {
        String response = mockMvc.perform(get("/api/v1/public/events"))
                .andExpect(status().isOk())
                .andReturn().getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return objectMapper.readTree(response);
    }

    private com.fasterxml.jackson.databind.JsonNode publicTimezoneDetails(
            EventEntity event
    ) throws Exception {
        String response = mockMvc.perform(get(
                        "/api/v1/public/events/{slug}", event.getPublicSlug()))
                .andExpect(status().isOk())
                .andReturn().getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return objectMapper.readTree(response);
    }

    private void assertPublicTimezone(
            com.fasterxml.jackson.databind.JsonNode row,
            EventEntity event,
            String timezone
    ) {
        assertEquals(event.getId().toString(), row.path("id").asText());
        assertEquals(timezone, row.path("venueTimezone").asText());
        assertEquals(
                event.getEventStartAt().toInstant(),
                OffsetDateTime.parse(row.path("eventStartAt").asText()).toInstant());
        assertEquals(
                event.getEventEndAt().toInstant(),
                OffsetDateTime.parse(row.path("eventEndAt").asText()).toInstant());
    }

    @Test
    void publicCatalogReturnsEachVenueTimezoneAndPreservesInstants() throws Exception {
        var east = publicTimezoneEvent("Asia/Vladivostok");
        var west = publicTimezoneEvent("Europe/Moscow");
        var expected = Map.of(
                east.getId().toString(), "Asia/Vladivostok",
                west.getId().toString(), "Europe/Moscow");
        var events = Map.of(
                east.getId().toString(), east,
                west.getId().toString(), west);
        var seen = new java.util.HashSet<String>();

        for (var row : publicTimezoneList()) {
            String id = row.path("id").asText();
            if (expected.containsKey(id)) {
                assertTrue(seen.add(id));
                assertPublicTimezone(row, events.get(id), expected.get(id));
            }
        }
        assertEquals(expected.keySet(), seen);

        assertPublicTimezone(publicTimezoneDetails(east), east, "Asia/Vladivostok");
        assertPublicTimezone(publicTimezoneDetails(west), west, "Europe/Moscow");

        for (var event : List.of(east, west)) {
            var stored = eventRepository.findById(event.getId()).orElseThrow();
            assertEquals(event.getEventStartAt().toInstant(),
                    stored.getEventStartAt().toInstant());
            assertEquals(event.getEventEndAt().toInstant(),
                    stored.getEventEndAt().toInstant());
        }
    }

    @Test
    void publicCatalogWithoutStoredVenueDoesNotInventTimezone() throws Exception {
        // Existing fixture deliberately references a nonexistent venue UUID.
        var event = eventRepository.findById(createEvent()).orElseThrow();
        assertTrue(!timezoneVenueRepository.existsById(event.getVenueId()));

        var details = publicTimezoneDetails(event);
        assertTrue(details.path("venueTimezone").isMissingNode()
                || details.path("venueTimezone").isNull());

        boolean found = false;
        for (var row : publicTimezoneList()) {
            if (event.getId().toString().equals(row.path("id").asText())) {
                found = true;
                assertTrue(row.path("venueTimezone").isMissingNode()
                        || row.path("venueTimezone").isNull());
            }
        }
        assertTrue(found);
    }

    @Test
    void timezoneLookupDoesNotExposeDraftEvent() throws Exception {
        var event = publicTimezoneEvent("Asia/Vladivostok");
        event.setStatus(EventStatus.DRAFT.name());
        eventRepository.saveAndFlush(event);

        for (var row : publicTimezoneList()) {
            org.junit.jupiter.api.Assertions.assertNotEquals(
                    event.getId().toString(), row.path("id").asText());
        }

        mockMvc.perform(get(
                        "/api/v1/public/events/{slug}", event.getPublicSlug()))
                .andExpect(status().isNotFound());
    }

    private UUID createEvent() {
        ManagedRegistrationOrganization managed =
                createManagedRegistrationOrganization();

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        EventEntity event = new EventEntity();
        event.setId(UUID.randomUUID());
        event.setCreatedByUserId(managed.organizerPrincipal().getUserId());
        event.setOrganizationId(managed.organizationId());
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

        UUID eventId = eventRepository.saveAndFlush(event).getId();
        registrationOrganizersByEvent.put(eventId, managed.organizerPrincipal());
        return eventId;
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
    private ManagedRegistrationOrganization createManagedRegistrationOrganization() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        UserEntity organizer = new UserEntity();
        organizer.setEmail("review-organizer-" + UUID.randomUUID() + "@example.com");
        organizer.setPhone("+79990000001");
        organizer.setPasswordHash("test-password-hash");
        organizer.setStatus("active");
        organizer = userRepository.saveAndFlush(organizer);

        OrganizationEntity organization = new OrganizationEntity();
        organization.setType("CLUB");
        organization.setName("Registration review fixture " + UUID.randomUUID());
        organization.setMeta("{}");
        organization.setCreatedAt(now);
        organization.setUpdatedAt(now);
        organization = organizationRepository.saveAndFlush(organization);

        OrganizationMemberEntity owner = new OrganizationMemberEntity();
        owner.setOrganizationId(organization.getId());
        owner.setUserId(organizer.getId());
        owner.setMemberRole("OWNER");
        owner.setCreatedAt(now);
        organizationMemberRepository.saveAndFlush(owner);

        PlatformUserPrincipal organizerPrincipal = new PlatformUserPrincipal(
                organizer.getId(),
                organizer.getEmail(),
                "",
                "active",
                List.of(new SimpleGrantedAuthority("ROLE_ORGANIZER"))
        );

        return new ManagedRegistrationOrganization(
                organization.getId(), organizerPrincipal
        );
    }

    private PlatformUserPrincipal registrationOrganizerForEvent(UUID eventId) {
        PlatformUserPrincipal organizer = registrationOrganizersByEvent.get(eventId);
        if (organizer == null) {
            throw new IllegalStateException(
                    "Registration organizer fixture not found for event " + eventId
            );
        }
        return organizer;
    }

    private record ManagedRegistrationOrganization(
            UUID organizationId,
            PlatformUserPrincipal organizerPrincipal
    ) {
    }
    private PlatformUserPrincipal registrationOrganizerForDiscipline(UUID disciplineId) {
        EventDisciplineEntity discipline = eventDisciplineRepository
                .findById(disciplineId)
                .orElseThrow(() -> new IllegalStateException(
                        "Discipline fixture not found: " + disciplineId
                ));
        return registrationOrganizerForEvent(discipline.getEventId());
    }


@Test void rejectsNonPositiveAndMalformedTimesBeforeCreatingResult() throws Exception {
    Fixture fixture = createFixture();
    for (String raw : List.of("0", "-0.00", "-12.5", "00:00.00", "01:60", "NaN", "Infinity")) {
        recordOnly(fixture, raw, "PENDING").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("results.invalid_raw_value"));
        org.junit.jupiter.api.Assertions.assertTrue(
                resultRepository.findByCompetitionUnitEntryId(fixture.entryId()).isEmpty());
    }
}

@Test void invalidTimeCannotOverwriteAnExistingResult() throws Exception {
    Fixture fixture = createFixture();
    recordAndRecalculate(fixture, "28.11", "PENDING", "ASC").andExpect(status().isOk());
    List<Map<String, Object>> before = unitResultRows(fixture.unitId());
    recordOnly(fixture, "-1", "PENDING").andExpect(status().isBadRequest());
    org.junit.jupiter.api.Assertions.assertEquals(before, unitResultRows(fixture.unitId()));
}

@Test void zeroAndNegativePointsRemainValidAndAreRanked() throws Exception {
    Fixture first = createFixture();
    Fixture second = additionalEntry(first, 2);
    rankingJdbc.update("UPDATE event_disciplines SET result_type='POINTS' WHERE id=?", first.disciplineId());
    mockMvc.perform(post("/api/v1/results").with(user(operatorFor(first)))
            .param("resultType", "POINTS").contentType(MediaType.APPLICATION_JSON)
            .content(resultJson(first.entryId(), "-2.50", "PENDING"))).andExpect(status().isOk());
    mockMvc.perform(post("/api/v1/results").with(user(operatorFor(first)))
            .param("resultType", "POINTS").contentType(MediaType.APPLICATION_JSON)
            .content(resultJson(second.entryId(), "0", "PENDING"))).andExpect(status().isOk());
    sqlRecalculate.execute(first.unitId(), "ASC");
    org.junit.jupiter.api.Assertions.assertEquals(Integer.valueOf(1), findResult(first).getFinalPlace());
    org.junit.jupiter.api.Assertions.assertEquals(Integer.valueOf(2), findResult(second).getFinalPlace());
}

@Test void typedExactParserAgreesWithJavaOnRepresentativeInputs() {
    var inputs = java.util.Arrays.asList(null, "", "0", "-0.00", "-12.5", "68.54", "01:08.54",
            "1:60", "1:-2", "1:+2", "1:2:3", "1e3", ".5", "1.", "NaN", "Infinity", " 12 ");
    for (String type : List.of("TIME", "POINTS")) {
        for (String raw : inputs) {
            java.math.BigDecimal expected = com.acme.sportplatform.common.numeric.NumericResultValue.parse(raw, type);
            java.math.BigDecimal actual = rankingJdbc.queryForObject(
                    "SELECT results_validated_numeric_value(?, ?)", java.math.BigDecimal.class, raw, type);
            if (expected == null) org.junit.jupiter.api.Assertions.assertNull(actual, type + ":" + raw);
            else {
                org.junit.jupiter.api.Assertions.assertNotNull(actual, type + ":" + raw);
                org.junit.jupiter.api.Assertions.assertEquals(0, expected.compareTo(actual), type + ":" + raw);
            }
        }
    }
}

@Test void exactParserMatchesClockAndDecimalAcrossBoundaryGrid() {
    Long mismatches = rankingJdbc.queryForObject("""
            SELECT count(*) FROM generate_series(0,30) m
            CROSS JOIN generate_series(0,59) s
            CROSS JOIN generate_series(0,99) h
            WHERE results_validated_numeric_value(
                m::text || ':' || lpad(s::text,2,'0') || '.' || lpad(h::text,2,'0'), 'TIME')
                IS DISTINCT FROM results_validated_numeric_value(
                (m*60+s)::text || '.' || lpad(h::text,2,'0'), 'TIME')
            """, Long.class);
    org.junit.jupiter.api.Assertions.assertEquals(Long.valueOf(0), mismatches);
}

}
