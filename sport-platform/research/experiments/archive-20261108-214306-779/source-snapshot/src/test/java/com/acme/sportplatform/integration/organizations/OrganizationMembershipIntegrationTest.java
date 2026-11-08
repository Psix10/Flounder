package com.acme.sportplatform.integration.organizations;
import java.util.*;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import org.springframework.test.web.servlet.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.acme.sportplatform.support.AbstractPostgresIntegrationTest;
import com.acme.sportplatform.identity.infrastructure.persistence.entity.UserEntity;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.UserRepository;
import com.acme.sportplatform.identity.infrastructure.security.PlatformUserPrincipal;
import com.acme.sportplatform.organizations.OrganizationManagementAccess;
import com.acme.sportplatform.organizations.infrastructure.jpa.*;
import com.fasterxml.jackson.databind.*;
@AutoConfigureMockMvc
class OrganizationMembershipIntegrationTest extends AbstractPostgresIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository users;
    @Autowired private OrganizationRepository organizations;
    @Autowired private OrganizationManagementAccess management;
    private record Actor(UUID id,PlatformUserPrincipal principal) { }
    private record Fixture(UUID organization,Actor admin,Actor target) { }
    private Actor actor(String role,String status) {
        var entity=new UserEntity();entity.setEmail("member-"+UUID.randomUUID()+"@example.com");entity.setStatus(status);entity.setPasswordHash("test-password-hash");
        entity=users.saveAndFlush(entity);UUID id=entity.getId();
        jdbc.update("""
                INSERT INTO user_role_assignments(id,user_id,role_id,created_at)
                SELECT ?,?,id,now() FROM roles WHERE upper(code)=? ORDER BY code LIMIT 1
                """,UUID.randomUUID(),id,role);
        return new Actor(id,new PlatformUserPrincipal(id,entity.getEmail(),"",status,List.of(new SimpleGrantedAuthority("ROLE_"+role))));
    }
    private Fixture fixture() {
        Actor admin=actor("PLATFORM_ADMIN","active"),target=actor("ORGANIZER","active");
        var organization=new OrganizationEntity();organization.setType("CLUB");organization.setName("Membership test "+UUID.randomUUID());
        organization.setMeta("{}");organization.setCreatedAt(OffsetDateTime.now());organization.setUpdatedAt(OffsetDateTime.now());
        return new Fixture(organizations.saveAndFlush(organization).getId(),admin,target);
    }
    private ResultActions assign(UUID organization,UUID target,String role,Actor actor) throws Exception {
        return mvc.perform(post("/api/v1/organizations/{id}/members",organization).with(user(actor.principal()))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("userId",target,"memberRole",role))));
    }
    private ResultActions assign(Fixture f,String role) throws Exception {return assign(f.organization(),f.target().id(),role,f.admin());}
    private List<Map<String,Object>> rows(Fixture f) {
        return jdbc.queryForList("SELECT * FROM organization_members WHERE organization_id=? ORDER BY id",f.organization());
    }
    @Test void assignsOwnerWithActorAndMakesOrganizationVisibleToOrganizer() throws Exception {
        var f=fixture();var response=json.readTree(assign(f,"OWNER").andExpect(status().isCreated()).andExpect(jsonPath("$.assignedByUserId").value(f.admin().id().toString()))
                .andReturn().getResponse().getContentAsString());
        assertEquals("OWNER",response.path("memberRole").asText());assertFalse(response.path("createdAt").asText().isBlank());
        assertTrue(management.canManageOrganization(f.organization(),f.target().id()));
        mvc.perform(get("/api/v1/organizations").with(user(f.target().principal()))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(f.organization().toString()));
    }
    @Test void adminMembershipGrantsOrganizationManagementWithoutChangingGlobalRoles() throws Exception {
        var f=fixture();var roles=jdbc.queryForList("SELECT * FROM user_role_assignments WHERE user_id=? ORDER BY id",f.target().id());
        assign(f,"ADMIN").andExpect(status().isCreated());assertTrue(management.canManageOrganization(f.organization(),f.target().id()));
        assertEquals(roles,jdbc.queryForList("SELECT * FROM user_role_assignments WHERE user_id=? ORDER BY id",f.target().id()));
    }
    @Test void sameRoleReplayReturns200AndKeepsTheWholeOriginalRow() throws Exception {
        var f=fixture();assign(f,"OWNER").andExpect(status().isCreated());var before=rows(f);
        assign(f,"OWNER").andExpect(status().isOk());assertEquals(before,rows(f));
    }
    @Test void replayByAnotherAdminDoesNotRewriteOriginalActor() throws Exception {
        var f=fixture();assign(f,"OWNER").andExpect(status().isCreated());var before=rows(f);var other=actor("PLATFORM_ADMIN","active");
        assign(f.organization(),f.target().id(),"OWNER",other).andExpect(status().isOk()).andExpect(jsonPath("$.assignedByUserId").value(f.admin().id().toString()));
        assertEquals(before,rows(f));
    }
    @Test void differentRoleIsConflictWithoutMutatingMembership() throws Exception {
        var f=fixture();assign(f,"ADMIN").andExpect(status().isCreated());var before=rows(f);
        assign(f,"OWNER").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("organizations.member_role_conflict"));assertEquals(before,rows(f));
    }
    @Test void missingOrganizationIs404() throws Exception {
        var f=fixture();assign(UUID.randomUUID(),f.target().id(),"OWNER",f.admin()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("organizations.member_organization_not_found"));assertTrue(rows(f).isEmpty());
    }
    @Test void missingTargetIs404() throws Exception {
        var f=fixture();assign(f.organization(),UUID.randomUUID(),"OWNER",f.admin()).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("organizations.member_user_not_found"));assertTrue(rows(f).isEmpty());
    }
    @Test void blockedTargetIs409() throws Exception {
        var f=fixture();var blocked=actor("ORGANIZER","blocked");assign(f.organization(),blocked.id(),"OWNER",f.admin()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("organizations.member_user_inactive"));assertTrue(rows(f).isEmpty());
    }
    @Test void invalidRolesAndMalformedUserIdAre400() throws Exception {
        var f=fixture();for(String role:List.of("PLATFORM_ADMIN","owner","OWNER ","MEMBER","")) assign(f,role).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/organizations/{id}/members",f.organization()).with(user(f.admin().principal())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"userId\":\"not-a-uuid\",\"memberRole\":\"OWNER\"}")).andExpect(status().isBadRequest());assertTrue(rows(f).isEmpty());
    }
    @Test void requiredBodyFieldsAreValidated() throws Exception {
        var f=fixture();for(String body:List.of("{}","{\"memberRole\":\"OWNER\"}","{\"userId\":\""+f.target().id()+"\"}"))
            mvc.perform(post("/api/v1/organizations/{id}/members",f.organization()).with(user(f.admin().principal())).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());assertTrue(rows(f).isEmpty());
    }
    @Test void anonymousCannotAssignOrList() throws Exception {
        var f=fixture();mvc.perform(post("/api/v1/organizations/{id}/members",f.organization()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("userId",f.target().id(),"memberRole","OWNER")))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/organizations/{id}/members",f.organization())).andExpect(status().isUnauthorized());assertTrue(rows(f).isEmpty());
    }
    @Test void ownerOrganizerOperatorAndParticipantCannotUseAdminAssignmentOrList() throws Exception {
        var f=fixture();assign(f,"OWNER").andExpect(status().isCreated());var before=rows(f);
        for(Actor caller:List.of(f.target(),actor("OPERATOR","active"),actor("PARTICIPANT","active"))) {
            assign(f.organization(),caller.id(),"ADMIN",caller).andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/organizations/{id}/members",f.organization()).with(user(caller.principal()))).andExpect(status().isForbidden());
        }
        assertEquals(before,rows(f));
    }
    @Test void administratorCanListOriginalAuditData() throws Exception {
        var f=fixture();assign(f,"OWNER").andExpect(status().isCreated());
        mvc.perform(get("/api/v1/organizations/{id}/members",f.organization()).with(user(f.admin().principal()))).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].userId").value(f.target().id().toString()))
                .andExpect(jsonPath("$[0].assignedByUserId").value(f.admin().id().toString()));
    }
    @Test void missingOrganizationListIs404() throws Exception {
        var f=fixture();mvc.perform(get("/api/v1/organizations/{id}/members",UUID.randomUUID()).with(user(f.admin().principal()))).andExpect(status().isNotFound());
    }
    @Test void missingOrBlockedActorCannotUseStaleAdministratorClaims() throws Exception {
        var f=fixture();var missing=new Actor(UUID.randomUUID(),null);
        var synthetic=new PlatformUserPrincipal(missing.id(),"missing@example.com","","active",List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN")));
        mvc.perform(post("/api/v1/organizations/{id}/members",f.organization()).with(user(synthetic)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(Map.of("userId",f.target().id(),"memberRole","OWNER")))).andExpect(status().isForbidden());
        var blocked=actor("PLATFORM_ADMIN","active");jdbc.update("UPDATE users SET status='blocked' WHERE id=?",blocked.id());
        assign(f.organization(),f.target().id(),"OWNER",blocked).andExpect(status().isForbidden());assertTrue(rows(f).isEmpty());
    }
    @Test void legacySqlMembershipIsReturnedWithoutInventingItsActor() throws Exception {
        var f=fixture();jdbc.update("INSERT INTO organization_members(id,organization_id,user_id,member_role,created_at) VALUES (?,?,?,'OWNER',now())",
                UUID.randomUUID(),f.organization(),f.target().id());var before=rows(f);
        var body=json.readTree(assign(f,"OWNER").andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertTrue(body.path("assignedByUserId").isNull()||body.path("assignedByUserId").isMissingNode());assertEquals(before,rows(f));
    }
    @Test void concurrentSameRoleAssignmentsReturnOneCreatedAndOneExisting() throws Exception {
        var f=fixture();var start=new java.util.concurrent.CountDownLatch(1);var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Integer> work=()->{start.await();return assign(f,"OWNER").andReturn().getResponse().getStatus();};
            var a=pool.submit(work);var b=pool.submit(work);start.countDown();
            var statuses=new ArrayList<>(List.of(a.get(20,java.util.concurrent.TimeUnit.SECONDS),b.get(20,java.util.concurrent.TimeUnit.SECONDS)));
            Collections.sort(statuses);assertEquals(List.of(200,201),statuses);assertEquals(1,rows(f).size());
        } finally {pool.shutdownNow();}
    }
    @Test void concurrentDifferentRolesDoNotSilentlyOverwriteWinner() throws Exception {
        var f=fixture();var start=new java.util.concurrent.CountDownLatch(1);var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var a=pool.submit(()->{start.await();return assign(f,"OWNER").andReturn().getResponse().getStatus();});
            var b=pool.submit(()->{start.await();return assign(f,"ADMIN").andReturn().getResponse().getStatus();});start.countDown();
            var statuses=new ArrayList<>(List.of(a.get(20,java.util.concurrent.TimeUnit.SECONDS),b.get(20,java.util.concurrent.TimeUnit.SECONDS)));
            Collections.sort(statuses);assertEquals(List.of(201,409),statuses);assertEquals(1,rows(f).size());
            assertTrue(Set.of("OWNER","ADMIN").contains(rows(f).get(0).get("member_role")));
        } finally {pool.shutdownNow();}
    }
}
