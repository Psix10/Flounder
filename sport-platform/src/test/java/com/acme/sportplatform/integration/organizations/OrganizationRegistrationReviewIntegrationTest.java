package com.acme.sportplatform.integration.organizations;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.sportplatform.identity.infrastructure.persistence.entity.UserEntity;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.UserRepository;
import com.acme.sportplatform.identity.infrastructure.security.PlatformUserPrincipal;
import com.acme.sportplatform.support.AbstractPostgresIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@AutoConfigureMockMvc
class OrganizationRegistrationReviewIntegrationTest
        extends AbstractPostgresIntegrationTest {

    private static final String URL =
            "/api/v1/organization-registration-requests";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserRepository users;

    private record Fixture(
            PlatformUserPrincipal admin,
            PlatformUserPrincipal applicant,
            UUID requestId,
            String organizationName
    ) { }

    private record HttpResult(
            int status,
            String body
    ) { }

    private PlatformUserPrincipal actor(String role) {
        var entity = new UserEntity();

        entity.setEmail(
                "org-review-" + UUID.randomUUID() + "@example.com"
        );
        entity.setPasswordHash("test-password-hash");
        entity.setStatus("active");

        entity = users.saveAndFlush(entity);

        return new PlatformUserPrincipal(
                entity.getId(),
                entity.getEmail(),
                "",
                "active",
                List.of(
                        new SimpleGrantedAuthority("ROLE_" + role)
                )
        );
    }

    private Fixture fixture() throws Exception {
        var admin = actor("PLATFORM_ADMIN");
        var applicant = actor("PARTICIPANT");

        String organizationName = "Review test " + UUID.randomUUID();

        var request = json.createObjectNode();
        request.put("organizationType", "CLUB");
        request.put("organizationName", organizationName);
        request.put("legalName", "Synthetic test organization");
        request.put("contactEmail", "synthetic@example.com");

        String response = mvc.perform(
                        post(URL)
                                .with(user(applicant))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsBytes(request))
                )
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        UUID requestId = UUID.fromString(
                json.readTree(response).path("id").asText()
        );

        return new Fixture(
                admin,
                applicant,
                requestId,
                organizationName
        );
    }

    private JsonNode approve(Fixture f) throws Exception {
        String response = mvc.perform(
                        post(URL + "/{id}/approve", f.requestId())
                                .with(user(f.admin()))
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return json.readTree(response);
    }

    private JsonNode reject(Fixture f, String reason) throws Exception {
        var body = json.createObjectNode();
        body.put("reason", reason);

        String response = mvc.perform(
                        post(URL + "/{id}/reject", f.requestId())
                                .with(user(f.admin()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json.writeValueAsBytes(body))
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return json.readTree(response);
    }

    private long organizationCount(Fixture f) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM organizations WHERE name = ?",
                Long.class,
                f.organizationName()
        ).longValue();
    }

    private long membershipCount(Fixture f) {
        return jdbc.queryForObject(
                """
                SELECT count(*)
                FROM organization_members m
                JOIN organizations o ON o.id = m.organization_id
                WHERE o.name = ? AND m.user_id = ?
                """,
                Long.class,
                f.organizationName(),
                f.applicant().getUserId()
        ).longValue();
    }

    private long organizerRoleCount(UUID applicantId) {
        return jdbc.queryForObject(
                """
                SELECT count(*)
                FROM user_role_assignments a
                JOIN roles r ON r.id = a.role_id
                WHERE a.user_id = ?
                  AND r.code = 'organizer'
                  AND a.event_id IS NULL
                  AND a.organization_id IS NULL
                """,
                Long.class,
                applicantId
        ).longValue();
    }

    private Map<String, Object> requestState(Fixture f) {
        return jdbc.queryForMap(
                """
                SELECT *
                FROM organization_registration_requests
                WHERE id = ?
                """,
                f.requestId()
        );
    }

    private void assertNoProvisioning(Fixture f) {
        assertEquals(0L, organizationCount(f));
        assertEquals(0L, membershipCount(f));
        assertEquals(
                0L,
                organizerRoleCount(f.applicant().getUserId())
        );
    }

    private void assertProvisioned(Fixture f, UUID organizationId) {
        assertEquals(1L, organizationCount(f));
        assertEquals(1L, membershipCount(f));
        assertEquals(
                1L,
                organizerRoleCount(f.applicant().getUserId())
        );

        Map<String, Object> member = jdbc.queryForMap(
                """
                SELECT member_role, assigned_by_user_id
                FROM organization_members
                WHERE organization_id = ? AND user_id = ?
                """,
                organizationId,
                f.applicant().getUserId()
        );

        assertEquals("OWNER", member.get("member_role"));
        assertEquals(
                f.admin().getUserId(),
                member.get("assigned_by_user_id")
        );

        Map<String, Object> savedRequest = requestState(f);

        assertEquals("APPROVED", savedRequest.get("status"));
        assertEquals(
                organizationId,
                savedRequest.get("organization_id")
        );
        assertEquals(
                f.admin().getUserId(),
                savedRequest.get("reviewed_by_user_id")
        );
        assertNotNull(savedRequest.get("reviewed_at"));
        assertNull(savedRequest.get("rejection_reason"));
    }

    @Test
    void approvalCreatesOrganizationOwnerAndOrganizerRole()
            throws Exception {
        var f = fixture();

        assertNoProvisioning(f);

        JsonNode response = approve(f);

        assertEquals(
                f.requestId().toString(),
                response.path("id").asText()
        );
        assertEquals("APPROVED", response.path("status").asText());

        UUID organizationId = UUID.fromString(
                response.path("organizationId").asText()
        );

        assertProvisioned(f, organizationId);

        Map<String, Object> organization = jdbc.queryForMap(
                """
                SELECT type, name, legal_name, contact_email
                FROM organizations
                WHERE id = ?
                """,
                organizationId
        );

        assertEquals("CLUB", organization.get("type"));
        assertEquals(f.organizationName(), organization.get("name"));
        assertEquals(
                "Synthetic test organization",
                organization.get("legal_name")
        );
        assertEquals(
                "synthetic@example.com",
                organization.get("contact_email")
        );
    }

    @Test
    void repeatedApprovalReturnsSameResultWithoutDuplicates()
            throws Exception {
        var f = fixture();

        JsonNode first = approve(f);
        var before = requestState(f);

        JsonNode repeated = approve(f);

        assertEquals(first, repeated);
        assertEquals(before, requestState(f));

        assertProvisioned(
                f,
                UUID.fromString(first.path("organizationId").asText())
        );
    }

    @Test
    void rejectionStoresReasonAndDoesNotProvisionAnything()
            throws Exception {
        var f = fixture();

        JsonNode first = reject(
                f,
                "  Please clarify organization contacts.  "
        );

        assertEquals("REJECTED", first.path("status").asText());
        assertEquals(
                "Please clarify organization contacts.",
                first.path("rejectionReason").asText()
        );
        assertFalse(
                first.hasNonNull("organizationId"),
                "Rejected request must not reference an organization"
        );
        assertEquals(
                f.admin().getUserId().toString(),
                first.path("reviewedByUserId").asText()
        );
        assertFalse(first.path("reviewedAt").isNull());

        var before = requestState(f);

        JsonNode repeated = reject(
                f,
                "Please clarify organization contacts."
        );

        assertEquals(first, repeated);
        assertEquals(before, requestState(f));

        assertNoProvisioning(f);
    }

    @Test
    void reviewedDecisionCannotBeChanged() throws Exception {
        var rejected = fixture();

        reject(rejected, "Clarification required.");
        var rejectedState = requestState(rejected);

        mvc.perform(
                        post(
                                URL + "/{id}/approve",
                                rejected.requestId()
                        )
                                .with(user(rejected.admin()))
                )
                .andExpect(status().isConflict());

        mvc.perform(
                        post(
                                URL + "/{id}/reject",
                                rejected.requestId()
                        )
                                .with(user(rejected.admin()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"reason":"Another reason"}
                                        """)
                )
                .andExpect(status().isConflict());

        assertEquals(rejectedState, requestState(rejected));
        assertNoProvisioning(rejected);

        var approved = fixture();
        JsonNode approval = approve(approved);
        var approvedState = requestState(approved);

        mvc.perform(
                        post(
                                URL + "/{id}/reject",
                                approved.requestId()
                        )
                                .with(user(approved.admin()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"reason":"Change the decision"}
                                        """)
                )
                .andExpect(status().isConflict());

        assertEquals(approvedState, requestState(approved));

        assertProvisioned(
                approved,
                UUID.fromString(
                        approval.path("organizationId").asText()
                )
        );
    }

    @Test
    void participantCannotListOrReviewRequests() throws Exception {
        var f = fixture();
        var before = requestState(f);

        mvc.perform(
                        get(URL)
                                .with(user(f.applicant()))
                )
                .andExpect(status().isForbidden());

        mvc.perform(
                        post(URL + "/{id}/approve", f.requestId())
                                .with(user(f.applicant()))
                )
                .andExpect(status().isForbidden());

        mvc.perform(
                        post(URL + "/{id}/reject", f.requestId())
                                .with(user(f.applicant()))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"reason":"Unauthorized review"}
                                        """)
                )
                .andExpect(status().isForbidden());

        assertEquals(before, requestState(f));
        assertNoProvisioning(f);
    }

    @Test
    void applicantSeesOwnHistoryButNotAnotherUsersRequests()
            throws Exception {
        var first = fixture();
        var second = fixture();

        reject(first, "Clarification required.");

        String response = mvc.perform(
                        get(URL + "/me")
                                .with(user(first.applicant()))
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode history = json.readTree(response);

        assertTrue(history.isArray());
        assertEquals(1, history.size());
        assertEquals(
                first.requestId().toString(),
                history.get(0).path("id").asText()
        );
        assertEquals(
                "REJECTED",
                history.get(0).path("status").asText()
        );
        assertNotEquals(
                second.requestId().toString(),
                history.get(0).path("id").asText()
        );
    }

    @Test
    void membershipFailureRollsBackOrganizationRoleAndDecision()
            throws Exception {
        var f = fixture();
        var before = requestState(f);

        assertNoProvisioning(f);

        String suffix = UUID.randomUUID()
                .toString()
                .replace("-", "");

        String function = "org_review_fail_" + suffix;
        String trigger = "org_review_trigger_" + suffix;

        jdbc.execute("""
                CREATE FUNCTION %s() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                  IF NEW.user_id = '%s'::uuid THEN
                    RAISE EXCEPTION 'injected owner membership failure';
                  END IF;
                  RETURN NEW;
                END;
                $$
                """.formatted(
                function,
                f.applicant().getUserId()
        ));

        try {
            jdbc.execute(
                    "CREATE TRIGGER " + trigger
                    + " BEFORE INSERT ON organization_members"
                    + " FOR EACH ROW EXECUTE FUNCTION "
                    + function + "()"
            );

            mvc.perform(
                            post(URL + "/{id}/approve", f.requestId())
                                    .with(user(f.admin()))
                    )
                    .andExpect(status().isInternalServerError());

            assertEquals(before, requestState(f));
            assertNoProvisioning(f);
        } finally {
            jdbc.execute(
                    "DROP TRIGGER IF EXISTS " + trigger
                    + " ON organization_members"
            );
            jdbc.execute(
                    "DROP FUNCTION IF EXISTS " + function + "()"
            );
        }
    }

    @Test
    void concurrentApprovalCreatesOnlyOneOrganization()
            throws Exception {
        var f = fixture();

        var executor = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);

        Callable<HttpResult> task = () -> {
            ready.countDown();

            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException(
                        "Concurrent approval start timed out"
                );
            }

            var response = mvc.perform(
                            post(URL + "/{id}/approve", f.requestId())
                                    .with(user(f.admin()))
                    )
                    .andReturn()
                    .getResponse();

            return new HttpResult(
                    response.getStatus(),
                    response.getContentAsString()
            );
        };

        try {
            var first = executor.submit(task);
            var second = executor.submit(task);

            assertTrue(
                    ready.await(10, TimeUnit.SECONDS),
                    "Both requests must be ready"
            );

            start.countDown();

            HttpResult firstResult = first.get(30, TimeUnit.SECONDS);
            HttpResult secondResult = second.get(30, TimeUnit.SECONDS);

            assertEquals(200, firstResult.status(), firstResult.body());
            assertEquals(200, secondResult.status(), secondResult.body());

            JsonNode firstBody = json.readTree(firstResult.body());
            JsonNode secondBody = json.readTree(secondResult.body());

            assertEquals(firstBody, secondBody);

            assertProvisioned(
                    f,
                    UUID.fromString(
                            firstBody.path("organizationId").asText()
                    )
            );
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void inactiveApplicantCannotBeApproved() throws Exception {
        var f = fixture();

        jdbc.update(
                "UPDATE users SET status = 'inactive' WHERE id = ?",
                f.applicant().getUserId()
        );

        var before = requestState(f);

        mvc.perform(
                        post(URL + "/{id}/approve", f.requestId())
                                .with(user(f.admin()))
                )
                .andExpect(status().isConflict());

        assertEquals(before, requestState(f));
        assertNoProvisioning(f);
    }
}