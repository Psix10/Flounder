package com.acme.sportplatform.integration.events;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.acme.sportplatform.events.EventOperationAccess;
import com.acme.sportplatform.events.domain.EventStatus;
import com.acme.sportplatform.events.infrastructure.persistence.entity.EventEntity;
import com.acme.sportplatform.events.infrastructure.persistence.entity.EventOperatorAssignmentEntity;
import com.acme.sportplatform.events.infrastructure.persistence.repository.EventOperatorAssignmentRepository;
import com.acme.sportplatform.events.infrastructure.persistence.repository.EventRepository;
import com.acme.sportplatform.identity.infrastructure.persistence.entity.RoleEntity;
import com.acme.sportplatform.identity.infrastructure.persistence.entity.UserEntity;
import com.acme.sportplatform.identity.infrastructure.persistence.entity.UserRoleAssignmentEntity;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.UserRepository;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.UserRoleAssignmentRepository;
import com.acme.sportplatform.identity.infrastructure.security.PlatformUserPrincipal;
import com.acme.sportplatform.organizations.infrastructure.jpa.OrganizationEntity;
import com.acme.sportplatform.organizations.infrastructure.jpa.OrganizationMemberEntity;
import com.acme.sportplatform.organizations.infrastructure.jpa.OrganizationMemberRepository;
import com.acme.sportplatform.organizations.infrastructure.jpa.OrganizationRepository;
import com.acme.sportplatform.support.AbstractPostgresIntegrationTest;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@AutoConfigureMockMvc
class EventOperatorIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final UUID SWIMMING_SPORT_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleAssignmentRepository userRoleAssignmentRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private OrganizationMemberRepository organizationMemberRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventOperatorAssignmentRepository assignmentRepository;

    @Autowired
    private EventOperationAccess eventOperationAccess;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void shouldAssignAndListOperatorForManagedEvent() throws Exception {
        Fixture fixture = createFixture();

        assign(fixture, fixture.owner())
                .andExpect(status().isNoContent());

        mockMvc.perform(get(
                        "/api/v1/events/{eventId}/operators",
                        fixture.eventId()
                )
                        .with(user(principal(fixture.owner(), "ORGANIZER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].eventId")
                        .value(fixture.eventId().toString()))
                .andExpect(jsonPath("$[0].operatorUserId")
                        .value(fixture.operator().getId().toString()))
                .andExpect(jsonPath("$[0].assignedByUserId")
                        .value(fixture.owner().getId().toString()));

        assertTrue(eventOperationAccess.isAssignedActiveOperator(
                fixture.eventId(),
                fixture.operator().getId()
        ));

        assertTrue(eventOperationAccess.canManageEvent(
                fixture.eventId(),
                fixture.owner().getId()
        ));

        assertFalse(eventOperationAccess.canManageEvent(
                fixture.eventId(),
                fixture.operator().getId()
        ));
    }

    @Test
    void shouldKeepOriginalAssignmentWhenAssignedTwice() throws Exception {
        Fixture fixture = createFixture();

        assign(fixture, fixture.owner())
                .andExpect(status().isNoContent());

        EventOperatorAssignmentEntity before = onlyAssignment(fixture.eventId());

        UUID originalId = before.getId();
        UUID originalAssignedBy = before.getAssignedByUserId();
        OffsetDateTime originalCreatedAt = before.getCreatedAt();

        UserEntity admin = createUser("active");
        grantGlobalRole(admin.getId(), "PLATFORM_ADMIN");

        mockMvc.perform(put(
                        "/api/v1/events/{eventId}/operators/{operatorUserId}",
                        fixture.eventId(),
                        fixture.operator().getId()
                )
                        .with(user(principal(admin, "PLATFORM_ADMIN"))))
                .andExpect(status().isNoContent());

        EventOperatorAssignmentEntity after = onlyAssignment(fixture.eventId());

        assertEquals(originalId, after.getId());
        assertEquals(originalAssignedBy, after.getAssignedByUserId());
        assertEquals(originalCreatedAt, after.getCreatedAt());
    }

    @Test
    void shouldRemoveAssignmentIdempotently() throws Exception {
        Fixture fixture = createFixture();

        assign(fixture, fixture.owner())
                .andExpect(status().isNoContent());

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(delete(
                            "/api/v1/events/{eventId}/operators/{operatorUserId}",
                            fixture.eventId(),
                            fixture.operator().getId()
                    )
                            .with(user(principal(fixture.owner(), "ORGANIZER"))))
                    .andExpect(status().isNoContent());
        }

        assertTrue(
                assignmentRepository
                        .findByEventIdOrderByCreatedAtAscIdAsc(fixture.eventId())
                        .isEmpty()
        );

        assertFalse(eventOperationAccess.isAssignedActiveOperator(
                fixture.eventId(),
                fixture.operator().getId()
        ));
    }

    @Test
    void shouldRejectUnrelatedOrganizerEvenWhenTheyCreatedEvent()
            throws Exception {

        Fixture fixture = createFixture();
        UserEntity outsider = createUser("active");

        grantGlobalRole(outsider.getId(), "ORGANIZER");

        EventEntity event = eventRepository.findById(fixture.eventId())
                .orElseThrow();

        event.setCreatedByUserId(outsider.getId());
        eventRepository.saveAndFlush(event);

        mockMvc.perform(put(
                        "/api/v1/events/{eventId}/operators/{operatorUserId}",
                        fixture.eventId(),
                        fixture.operator().getId()
                )
                        .with(user(principal(outsider, "ORGANIZER"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(
                        "/api/v1/events/{eventId}/operators",
                        fixture.eventId()
                )
                        .with(user(principal(outsider, "ORGANIZER"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete(
                        "/api/v1/events/{eventId}/operators/{operatorUserId}",
                        fixture.eventId(),
                        fixture.operator().getId()
                )
                        .with(user(principal(outsider, "ORGANIZER"))))
                .andExpect(status().isForbidden());

        assertFalse(eventOperationAccess.canManageEvent(
                fixture.eventId(),
                outsider.getId()
        ));

        assertTrue(
                assignmentRepository
                        .findByEventIdOrderByCreatedAtAscIdAsc(fixture.eventId())
                        .isEmpty()
        );
    }

    @Test
    void shouldAllowPlatformAdminWithoutOrganizationMembership()
            throws Exception {

        Fixture fixture = createFixture();
        UserEntity admin = createUser("active");

        grantGlobalRole(admin.getId(), "PLATFORM_ADMIN");

        mockMvc.perform(put(
                        "/api/v1/events/{eventId}/operators/{operatorUserId}",
                        fixture.eventId(),
                        fixture.operator().getId()
                )
                        .with(user(principal(admin, "PLATFORM_ADMIN"))))
                .andExpect(status().isNoContent());

        assertEquals(
                admin.getId(),
                onlyAssignment(fixture.eventId()).getAssignedByUserId()
        );

        mockMvc.perform(delete(
                        "/api/v1/events/{eventId}/operators/{operatorUserId}",
                        fixture.eventId(),
                        fixture.operator().getId()
                )
                        .with(user(principal(admin, "PLATFORM_ADMIN"))))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldRejectUserWithoutGlobalOperatorRole() throws Exception {
        Fixture fixture = createFixture();
        UserEntity participant = createUser("active");

        grantGlobalRole(participant.getId(), "PARTICIPANT");

        mockMvc.perform(put(
                        "/api/v1/events/{eventId}/operators/{operatorUserId}",
                        fixture.eventId(),
                        participant.getId()
                )
                        .with(user(principal(fixture.owner(), "ORGANIZER"))))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.code")
                        .value("events.operator_not_eligible"));

        assertFalse(
                assignmentRepository.existsByEventIdAndOperatorUserId(
                        fixture.eventId(),
                        participant.getId()
                )
        );
    }

    @Test
    void shouldRejectInactiveOperatorAndAllowRemovingOldAssignment()
            throws Exception {

        Fixture fixture = createFixture();

        assign(fixture, fixture.owner())
                .andExpect(status().isNoContent());

        UserEntity operator = userRepository
                .findById(fixture.operator().getId())
                .orElseThrow();

        operator.setStatus("blocked");
        userRepository.saveAndFlush(operator);

        assertFalse(eventOperationAccess.isAssignedActiveOperator(
                fixture.eventId(),
                operator.getId()
        ));

        assign(fixture, fixture.owner())
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.code")
                        .value("events.operator_not_eligible"));

        mockMvc.perform(delete(
                        "/api/v1/events/{eventId}/operators/{operatorUserId}",
                        fixture.eventId(),
                        operator.getId()
                )
                        .with(user(principal(fixture.owner(), "ORGANIZER"))))
                .andExpect(status().isNoContent());

        assertTrue(
                assignmentRepository
                        .findByEventIdOrderByCreatedAtAscIdAsc(fixture.eventId())
                        .isEmpty()
        );
    }

    @Test
    void shouldRejectEventAndOrganizationScopedRolesAsGlobalEligibility()
            throws Exception {

        Fixture fixture = createFixture();

        UserEntity eventScopedOperator = createUser("active");
        grantRole(
                eventScopedOperator.getId(),
                "OPERATOR",
                fixture.eventId(),
                null
        );

        UserEntity organizationScopedOperator = createUser("active");
        grantRole(
                organizationScopedOperator.getId(),
                "OPERATOR",
                null,
                fixture.organizationId()
        );

        for (UserEntity target : List.of(
                eventScopedOperator,
                organizationScopedOperator
        )) {
            mockMvc.perform(put(
                            "/api/v1/events/{eventId}/operators/{operatorUserId}",
                            fixture.eventId(),
                            target.getId()
                    )
                            .with(user(principal(fixture.owner(), "ORGANIZER"))))
                    .andExpect(status().is4xxClientError())
                    .andExpect(jsonPath("$.code")
                            .value("events.operator_not_eligible"));

            assertFalse(
                    assignmentRepository.existsByEventIdAndOperatorUserId(
                            fixture.eventId(),
                            target.getId()
                    )
            );
        }
    }

    @Test
    void shouldNotGrantAccessToAnotherEvent() throws Exception {
        Fixture fixture = createFixture();

        assign(fixture, fixture.owner())
                .andExpect(status().isNoContent());

        UUID anotherEventId = createEvent(
                fixture.organizationId(),
                fixture.owner().getId()
        );

        assertTrue(eventOperationAccess.isAssignedActiveOperator(
                fixture.eventId(),
                fixture.operator().getId()
        ));

        assertFalse(eventOperationAccess.isAssignedActiveOperator(
                anotherEventId,
                fixture.operator().getId()
        ));
    }

    @Test
    void shouldRevokeEligibilityAfterGlobalOperatorRoleIsRemoved()
            throws Exception {

        Fixture fixture = createFixture();

        assign(fixture, fixture.owner())
                .andExpect(status().isNoContent());

        assertTrue(eventOperationAccess.isAssignedActiveOperator(
                fixture.eventId(),
                fixture.operator().getId()
        ));

        List<UserRoleAssignmentEntity> assignments =
                userRoleAssignmentRepository.findAll().stream()
                        .filter(assignment -> fixture.operator().getId()
                                .equals(assignment.getUserId()))
                        .filter(assignment -> assignment.getEventId() == null)
                        .filter(assignment -> assignment.getOrganizationId() == null)
                        .toList();

        userRoleAssignmentRepository.deleteAll(assignments);
        userRoleAssignmentRepository.flush();

        assertFalse(eventOperationAccess.isAssignedActiveOperator(
                fixture.eventId(),
                fixture.operator().getId()
        ));

        assertTrue(
                assignmentRepository.existsByEventIdAndOperatorUserId(
                        fixture.eventId(),
                        fixture.operator().getId()
                )
        );
    }

    @Test
    void shouldAllowOrganizationAdminToAssignOperator() throws Exception {
        Fixture fixture = createFixture();
        UserEntity organizationAdmin = createUser("active");

        grantGlobalRole(organizationAdmin.getId(), "ORGANIZER");
        addMember(
                fixture.organizationId(),
                organizationAdmin.getId(),
                "ADMIN"
        );

        assign(fixture, organizationAdmin)
                .andExpect(status().isNoContent());

        assertEquals(
                organizationAdmin.getId(),
                onlyAssignment(fixture.eventId()).getAssignedByUserId()
        );
    }

    @Test
    void shouldRejectOperatorFromManagingAssignments() throws Exception {
        Fixture fixture = createFixture();

        assign(fixture, fixture.owner())
                .andExpect(status().isNoContent());

        mockMvc.perform(get(
                        "/api/v1/events/{eventId}/operators",
                        fixture.eventId()
                )
                        .with(user(principal(fixture.operator(), "OPERATOR"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(put(
                        "/api/v1/events/{eventId}/operators/{operatorUserId}",
                        fixture.eventId(),
                        fixture.operator().getId()
                )
                        .with(user(principal(fixture.operator(), "OPERATOR"))))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete(
                        "/api/v1/events/{eventId}/operators/{operatorUserId}",
                        fixture.eventId(),
                        fixture.operator().getId()
                )
                        .with(user(principal(fixture.operator(), "OPERATOR"))))
                .andExpect(status().isForbidden());

        assertTrue(
                assignmentRepository.existsByEventIdAndOperatorUserId(
                        fixture.eventId(),
                        fixture.operator().getId()
                )
        );
    }

    private ResultActions assign(
            Fixture fixture,
            UserEntity actor
    ) throws Exception {
        return mockMvc.perform(put(
                        "/api/v1/events/{eventId}/operators/{operatorUserId}",
                        fixture.eventId(),
                        fixture.operator().getId()
                )
                .with(user(principal(actor, "ORGANIZER"))));
    }

    private EventOperatorAssignmentEntity onlyAssignment(UUID eventId) {
        List<EventOperatorAssignmentEntity> assignments = assignmentRepository
                .findByEventIdOrderByCreatedAtAscIdAsc(eventId);

        assertEquals(1, assignments.size());

        return assignments.get(0);
    }

    private Fixture createFixture() {
        UserEntity owner = createUser("active");
        UserEntity operator = createUser("active");

        grantGlobalRole(owner.getId(), "ORGANIZER");
        grantGlobalRole(operator.getId(), "OPERATOR");

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        OrganizationEntity organization = new OrganizationEntity();
        organization.setType("organizer");
        organization.setName("Operator IT " + UUID.randomUUID());
        organization.setContactEmail(owner.getEmail());
        organization.setMeta("{}");
        organization.setCreatedAt(now);
        organization.setUpdatedAt(now);

        UUID organizationId = organizationRepository
                .saveAndFlush(organization)
                .getId();

        addMember(organizationId, owner.getId(), "OWNER");

        UUID eventId = createEvent(
                organizationId,
                owner.getId()
        );

        return new Fixture(
                organizationId,
                eventId,
                owner,
                operator
        );
    }

    private UserEntity createUser(String status) {
        String suffix = UUID.randomUUID().toString();

        UserEntity user = new UserEntity();
        user.setEmail("operator-it-" + suffix + "@example.com");
        user.setPasswordHash("test-password-hash");
        user.setStatus(status);

        return userRepository.saveAndFlush(user);
    }

    private void addMember(
            UUID organizationId,
            UUID userId,
            String memberRole
    ) {
        OrganizationMemberEntity member = new OrganizationMemberEntity();
        member.setOrganizationId(organizationId);
        member.setUserId(userId);
        member.setMemberRole(memberRole);
        member.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));

        organizationMemberRepository.saveAndFlush(member);
    }

    private UUID createEvent(
            UUID organizationId,
            UUID createdByUserId
    ) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        EventEntity event = new EventEntity();
        event.setId(UUID.randomUUID());
        event.setCreatedByUserId(createdByUserId);
        event.setOrganizationId(organizationId);
        event.setVenueId(UUID.randomUUID());
        event.setSportId(SWIMMING_SPORT_ID);
        event.setRegulationVersionId(UUID.randomUUID());
        event.setTitle("Event operator IT");
        event.setDescription("Fixture for event operator access tests");
        event.setRegistrationOpenAt(now.minusDays(1));
        event.setRegistrationCloseAt(now.plusDays(20));
        event.setEventStartAt(now.plusDays(30));
        event.setEventEndAt(now.plusDays(31));
        event.setStatus(EventStatus.REGISTRATION_OPEN.name());
        event.setPublicSlug("event-operator-it-" + UUID.randomUUID());
        event.setSettingsJson("{}");
        event.setCreatedAt(now);
        event.setUpdatedAt(now);

        return eventRepository.saveAndFlush(event).getId();
    }

    private void grantGlobalRole(UUID userId, String roleCode) {
        grantRole(userId, roleCode, null, null);
    }

    private void grantRole(
            UUID userId,
            String roleCode,
            UUID eventId,
            UUID organizationId
    ) {
        TransactionTemplate transaction = new TransactionTemplate(
                transactionManager
        );

        transaction.executeWithoutResult(transactionStatus -> {
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
            assignment.setEventId(eventId);
            assignment.setOrganizationId(organizationId);

            userRoleAssignmentRepository.saveAndFlush(assignment);
        });
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

    private record Fixture(
            UUID organizationId,
            UUID eventId,
            UserEntity owner,
            UserEntity operator
    ) {
    }
}