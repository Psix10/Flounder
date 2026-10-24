package com.acme.sportplatform.integration.results;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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

import com.acme.sportplatform.competition.domain.EventDisciplineStatus;
import com.acme.sportplatform.competition.infrastructure.jpa.EventDisciplineEntity;
import com.acme.sportplatform.competition.infrastructure.jpa.EventDisciplineRepository;
import com.acme.sportplatform.events.domain.EventStatus;
import com.acme.sportplatform.events.infrastructure.persistence.entity.EventEntity;
import com.acme.sportplatform.events.infrastructure.persistence.repository.EventRepository;
import com.acme.sportplatform.identity.infrastructure.persistence.entity.ProfileEntity;
import com.acme.sportplatform.identity.infrastructure.persistence.entity.UserEntity;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.ProfileRepository;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.UserRepository;
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

@AutoConfigureMockMvc
class ResultsIntegrationTest extends AbstractPostgresIntegrationTest {

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
    private EventRepository eventRepository;

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

        for (String route : routes) {
            mockMvc.perform(get(
                            route,
                            wrongEventId,
                            fixture.disciplineId()
                    )
                            .with(user(principal("OPERATOR"))))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code")
                            .value("results.public_not_found"));

            mockMvc.perform(get(
                            route,
                            fixture.eventId(),
                            fixture.disciplineId()
                    )
                            .with(user(principal("OPERATOR"))))
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
                        .with(user(principal("OPERATOR")))
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

    private ResultActions recordOnly(
            Fixture fixture,
            String rawValue,
            String resultStatus
    ) throws Exception {
        return mockMvc.perform(post("/api/v1/results")
                .with(user(principal("OPERATOR")))
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
                .with(user(principal("OPERATOR")))
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
                .with(user(principal("ORGANIZER")))
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

        String suffix = UUID.randomUUID().toString();

        UserEntity participant = new UserEntity();
        participant.setEmail("results-" + suffix + "@example.com");
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
                new PlatformUserPrincipal(
                        participant.getId(),
                        participant.getEmail(),
                        "",
                        "active",
                        List.of(new SimpleGrantedAuthority("ROLE_PARTICIPANT"))
                );

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