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
class FinalStandingsIntegrationTest extends AbstractPostgresIntegrationTest {

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

    @Autowired private org.springframework.jdbc.core.JdbcTemplate finalJdbc;
    @Autowired private javax.sql.DataSource finalDataSource;
    private PlatformUserPrincipal finalAdmin;
    private PlatformUserPrincipal finalAdmin() {
        if(finalAdmin==null) finalAdmin=principal(createUser("final-admin"),"PLATFORM_ADMIN");
        return finalAdmin;
    }
    private String finalRoute(Fixture f) { return "/api/v1/events/"+f.eventId()+"/disciplines/"+f.disciplineId()+"/finalizations"; }
    private String publicFinalRoute(Fixture f) { return "/api/v1/public/events/"+f.eventId()+"/disciplines/"+f.disciplineId()+"/final-standings"; }
    private ResultActions confirmRequest(Fixture f,UUID request) throws Exception {
        return mockMvc.perform(post(finalRoute(f)).with(user(finalAdmin())).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of("requestId",request,"programmeComplete",true,"note","checked"))));
    }
    private com.fasterxml.jackson.databind.JsonNode confirm(Fixture f,UUID request) throws Exception {
        return objectMapper.readTree(confirmRequest(f,request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private ResultActions revokeRequest(Fixture f,String id,String reason) throws Exception {
        return mockMvc.perform(post(finalRoute(f)+"/"+id+"/revoke").with(user(finalAdmin())).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of("reason",reason))));
    }
    private Fixture ready() throws Exception {
        var f=createFixture();category(f,"A","G");recordAndRecalculate(f,"30","PENDING","ASC").andExpect(status().isOk());
        publish(f).andExpect(status().isNoContent());return f;
    }
    private String sqlState(Throwable ex) {
        for(Throwable t=ex;t!=null;t=t.getCause()) if(t instanceof java.sql.SQLException s) return s.getSQLState();
        return null;
    }
    @Test void confirmsAndPubliclyReadsSavedCategoryStandingsAndAuditSource() throws Exception {
        var f=ready();var saved=confirm(f,UUID.randomUUID());
        assertEquals("CONFIRMED",saved.path("status").asText());assertEquals(1,saved.path("revision").asInt());
        assertEquals(1,saved.path("standings").path("entries").get(0).path("categoryPlace").asInt());
        var read=objectMapper.readTree(mockMvc.perform(get(publicFinalRoute(f))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(saved,read);
        String input=finalJdbc.queryForObject("SELECT source_json::text FROM final_standings_snapshots WHERE id=?",String.class,UUID.fromString(saved.path("id").asText()));
        assertEquals(f.entryId().toString(),objectMapper.readTree(input).path("entries").get(0).path("id").asText());
        assertEquals(1,objectMapper.readTree(input).path("units").size());
    }
    @Test void rejectsUnpublishedHeatWithoutSnapshot() throws Exception {
        var f=createFixture();confirmRequest(f,UUID.randomUUID()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("results.final_not_ready"));
        assertEquals(0,finalJdbc.queryForObject("SELECT count(*) FROM final_standings_snapshots WHERE event_discipline_id=?",Integer.class,f.disciplineId()));
    }
    @Test void rejectsEmptyProgramme() throws Exception {
        UUID event=createEvent();UUID discipline=createDiscipline(event,createDisciplineTemplate());
        var f=new Fixture(event,discipline,UUID.randomUUID(),UUID.randomUUID());
        confirmRequest(f,UUID.randomUUID()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("results.final_not_ready"));
    }
    @Test void rejectsConfirmedRegistrationWithoutAssignment() throws Exception {
        var f=ready();createConfirmedRegistration(f.disciplineId());
        confirmRequest(f,UUID.randomUUID()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("results.final_not_ready"));
    }
    @Test void repeatedRequestReturnsSameSnapshotAndOneRevision() throws Exception {
        var f=ready();UUID key=UUID.randomUUID();var first=confirm(f,key);assertEquals(first,confirm(f,key));
        assertEquals(1,finalJdbc.queryForObject("SELECT count(*) FROM final_standings_snapshots WHERE event_discipline_id=?",Integer.class,f.disciplineId()));
    }
    @Test void differentRequestCannotOverwriteConfirmedSnapshot() throws Exception {
        var f=ready();var first=confirm(f,UUID.randomUUID());
        confirmRequest(f,UUID.randomUUID()).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("results.final_already_confirmed"));
        assertEquals(1,finalJdbc.queryForObject("SELECT count(*) FROM final_standings_snapshots WHERE event_discipline_id=?",Integer.class,f.disciplineId()));
    }
    @Test void operatorCannotFinalizeOrReadPrivateHistory() throws Exception {
        var f=ready();
        mockMvc.perform(post(finalRoute(f)).with(user(operatorFor(f))).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(Map.of("requestId",UUID.randomUUID(),"programmeComplete",true))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(finalRoute(f)).with(user(operatorFor(f)))).andExpect(status().isForbidden());
    }
    @Test void wrongEventDoesNotExposeFinalSnapshot() throws Exception {
        var f=ready();confirm(f,UUID.randomUUID());
        mockMvc.perform(get("/api/v1/public/events/"+UUID.randomUUID()+"/disciplines/"+f.disciplineId()+"/final-standings"))
                .andExpect(status().isNotFound());
    }
    @Test void revocationRequiresNonblankReason() throws Exception {
        var f=ready();var saved=confirm(f,UUID.randomUUID());revokeRequest(f,saved.path("id").asText()," ").andExpect(status().isBadRequest());
        mockMvc.perform(get(publicFinalRoute(f))).andExpect(status().isOk());
    }
    @Test void revocationPreservesHistoryAndCorrectionCreatesNewRevision() throws Exception {
        var f=ready();UUID request=UUID.randomUUID();var first=confirm(f,request);
        revokeRequest(f,first.path("id").asText(),"Correction required").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REVOKED"));
        mockMvc.perform(get(publicFinalRoute(f))).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/competition-units/{id}/publication",f.unitId()).with(user(operatorFor(f)))
                .contentType(MediaType.APPLICATION_JSON).content("{\"published\":false}")).andExpect(status().isNoContent());
        recordAndRecalculate(f,"29","PENDING","ASC").andExpect(status().isOk());publish(f).andExpect(status().isNoContent());
        var second=confirm(f,UUID.randomUUID());assertEquals(2,second.path("revision").asInt());
        assertEquals("29",second.path("standings").path("entries").get(0).path("rawValue").asText());
        var history=objectMapper.readTree(mockMvc.perform(get(finalRoute(f)).with(user(finalAdmin()))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(2,history.size());assertEquals("30",history.get(0).path("standings").path("entries").get(0).path("rawValue").asText());
        assertEquals("Correction required",history.get(0).path("revocationReason").asText());
        assertEquals("REVOKED",confirm(f,request).path("status").asText());
        revokeRequest(f,first.path("id").asText(),"Correction required").andExpect(status().isOk());
        assertEquals("CONFIRMED",objectMapper.readTree(mockMvc.perform(get(publicFinalRoute(f))).andReturn().getResponse().getContentAsString()).path("status").asText());
    }
    @Test void storedPayloadAndSourceCannotBeOverwrittenOrDeleted() throws Exception {
        var f=ready();var saved=confirm(f,UUID.randomUUID());UUID id=UUID.fromString(saved.path("id").asText());
        for(String command:List.of("UPDATE final_standings_snapshots SET standings_json='{}'::jsonb WHERE id=?",
                "UPDATE final_standings_snapshots SET source_json='{}'::jsonb WHERE id=?","DELETE FROM final_standings_snapshots WHERE id=?")) {
            var ex=org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataAccessException.class,()->finalJdbc.update(command,id));
            assertEquals("PZ002",sqlState(ex));
        }
    }
    @Test void frozenHeatCannotBeUnpublishedViaExistingApi() throws Exception {
        var f=ready();confirm(f,UUID.randomUUID());
        mockMvc.perform(post("/api/v1/competition-units/{id}/publication",f.unitId()).with(user(operatorFor(f)))
                .contentType(MediaType.APPLICATION_JSON).content("{\"published\":false}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("results.discipline_finalized"));
    }
    @Test void frozenResultAssignmentAndNewHeatAreProtectedAtDatabaseLevel() throws Exception {
        var f=ready();confirm(f,UUID.randomUUID());
        var commands=List.of("UPDATE results SET raw_value='20' WHERE competition_unit_entry_id=?",
                "UPDATE competition_unit_entries SET category_code='OTHER' WHERE id=?");
        for(String command:commands) {
            var ex=org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataAccessException.class,()->finalJdbc.update(command,f.entryId()));
            assertEquals("PZ001",sqlState(ex));
        }
        var ex=org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataAccessException.class,()->finalJdbc.update(
                "INSERT INTO competition_units(id,event_discipline_id,label,sequence_number) VALUES (?,?,?,?)",UUID.randomUUID(),f.disciplineId(),"Blocked",2));
        assertEquals("PZ001",sqlState(ex));
    }
    @Test void confirmedRegistrationSnapshotCannotChangeAfterFinalization() throws Exception {
        var f=ready();confirm(f,UUID.randomUUID());
        var ex=org.junit.jupiter.api.Assertions.assertThrows(org.springframework.dao.DataAccessException.class,()->finalJdbc.update(
                "UPDATE registrations SET participant_snapshot='{}'::jsonb WHERE id=(SELECT registration_id FROM competition_unit_entries WHERE id=?)",f.entryId()));
        assertEquals("PZ001",sqlState(ex));
    }
    @Test void concurrentConfirmationsCreateOnlyOneActiveSnapshot() throws Exception {
        var f=ready();var admin=finalAdmin();var start=new java.util.concurrent.CountDownLatch(1);
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Integer> work=()->{
                start.await();return mockMvc.perform(post(finalRoute(f)).with(user(admin)).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of("requestId",UUID.randomUUID(),"programmeComplete",true))))
                        .andReturn().getResponse().getStatus();
            };
            var a=pool.submit(work);var b=pool.submit(work);start.countDown();
            var statuses=new java.util.ArrayList<>(List.of(a.get(20,java.util.concurrent.TimeUnit.SECONDS),b.get(20,java.util.concurrent.TimeUnit.SECONDS)));
            java.util.Collections.sort(statuses);assertEquals(List.of(200,409),statuses);
            assertEquals(1,finalJdbc.queryForObject("SELECT count(*) FROM final_standings_snapshots WHERE event_discipline_id=?",Integer.class,f.disciplineId()));
        } finally {pool.shutdownNow();}
    }
    @Test void writerWaitingBehindFinalizationIsRejectedAfterConfirmationCommits() throws Exception {
        var f=ready();var admin=finalAdmin();String payload=objectMapper.writeValueAsString(standings(f));
        var pool=java.util.concurrent.Executors.newSingleThreadExecutor();
        try(var holder=finalDataSource.getConnection();var worker=finalDataSource.getConnection()) {
            holder.setAutoCommit(false);worker.setAutoCommit(false);
            int holderPid,workerPid;
            try(var st=holder.createStatement();var rs=st.executeQuery("SELECT pg_backend_pid()")){rs.next();holderPid=rs.getInt(1);}
            try(var st=worker.createStatement();var rs=st.executeQuery("SELECT pg_backend_pid()")){rs.next();workerPid=rs.getInt(1);}
            try {
                try(var st=holder.prepareStatement("SELECT results_finalization_lock(?)")){st.setObject(1,f.disciplineId());st.execute();}
                var future=pool.submit(()->{
                    try(var st=worker.prepareStatement("UPDATE results SET raw_value='20' WHERE competition_unit_entry_id=?")) {
                        st.setObject(1,f.entryId());st.executeUpdate();worker.commit();return "COMMITTED";
                    } catch(java.sql.SQLException ex) {worker.rollback();return ex.getSQLState();}
                });
                boolean blocked=false;long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(8);
                while(System.nanoTime()<deadline) {
                    if(Boolean.TRUE.equals(finalJdbc.queryForObject("SELECT ? = ANY(pg_blocking_pids(?))",Boolean.class,holderPid,workerPid))) {blocked=true;break;}
                    Thread.sleep(20);
                }
                assertTrue(blocked,"Writer must wait for the finalization's exclusive advisory lock");
                try(var st=holder.prepareStatement("""
                    INSERT INTO final_standings_snapshots
                    (id,event_id,event_discipline_id,request_id,revision_no,status,confirmed_by,confirmed_at,confirmation_note,standings_json,source_json)
                    VALUES (?,?,?,?,1,'CONFIRMED',?,clock_timestamp(),'checked',CAST(? AS jsonb),results_finalization_source(?))
                    """)) {
                    st.setObject(1,UUID.randomUUID());st.setObject(2,f.eventId());st.setObject(3,f.disciplineId());
                    st.setObject(4,UUID.randomUUID());st.setObject(5,admin.getUserId());st.setString(6,payload);st.setObject(7,f.disciplineId());st.executeUpdate();
                }
                holder.commit();assertEquals("PZ001",future.get(10,java.util.concurrent.TimeUnit.SECONDS));
                assertEquals("30",findResult(f).getRawValue());
            } finally {holder.rollback();worker.rollback();}
        } finally {pool.shutdownNow();}
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
                finalJdbc, UUID.fromString(registrationId),
                registrationOrganizerForDiscipline(disciplineId).getUserId());

        return UUID.fromString(registrationId);
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

}
