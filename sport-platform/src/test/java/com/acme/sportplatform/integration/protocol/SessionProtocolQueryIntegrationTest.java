package com.acme.sportplatform.integration.protocol;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.sportplatform.events.infrastructure.persistence.entity.EventEntity;
import com.acme.sportplatform.events.infrastructure.persistence.repository.EventRepository;
import com.acme.sportplatform.identity.infrastructure.persistence.entity.UserEntity;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.UserRepository;
import com.acme.sportplatform.identity.infrastructure.security.PlatformUserPrincipal;
import com.acme.sportplatform.support.AbstractPostgresIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@AutoConfigureMockMvc
class SessionProtocolQueryIntegrationTest
        extends AbstractPostgresIntegrationTest {

    private static final UUID SPORT = UUID.fromString(
            "10000000-0000-0000-0000-000000000001"
    );

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserRepository users;

    @Autowired
    private EventRepository events;

    private PlatformUserPrincipal actor(String role) {
        var entity = new UserEntity();

        entity.setEmail(
                "session-query-" + UUID.randomUUID() + "@example.com"
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

    private UUID event(PlatformUserPrincipal owner) {
        var now = OffsetDateTime.now(ZoneOffset.UTC);

        var entity = new EventEntity();
        entity.setId(UUID.randomUUID());
        entity.setCreatedByUserId(owner.getUserId());
        entity.setOrganizationId(UUID.randomUUID());
        entity.setVenueId(UUID.randomUUID());
        entity.setSportId(SPORT);
        entity.setRegulationVersionId(UUID.randomUUID());
        entity.setTitle("Session query integration test");
        entity.setDescription("Synthetic query fixture");
        entity.setRegistrationOpenAt(now.minusDays(1));
        entity.setRegistrationCloseAt(now.plusDays(10));
        entity.setEventStartAt(now.plusDays(20));
        entity.setEventEndAt(now.plusDays(21));
        entity.setStatus("REGISTRATION_OPEN");
        entity.setPublicSlug("session-query-" + UUID.randomUUID());
        entity.setSettingsJson("{}");
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        return events.saveAndFlush(entity).getId();
    }

    private ObjectNode snapshot(
            UUID eventId,
            int revision,
            PlatformUserPrincipal creator
    ) {
        UUID snapshotId = UUID.randomUUID();

        var protocol = json.createObjectNode();
        protocol.put("snapshotId", snapshotId.toString());
        protocol.put("eventId", eventId.toString());
        protocol.put("revisionNo", revision);
        protocol.putArray("disciplines");
        protocol.putArray("schedule");

        var input = json.createObjectNode();
        input.put(
                "privateTestMarker",
                "INPUT_MUST_NOT_BE_EXPOSED"
        );

        jdbc.update(
                """
                INSERT INTO session_protocol_snapshots (
                    id,
                    event_id,
                    revision_no,
                    input_json,
                    protocol_json,
                    created_by_user_id,
                    created_at
                )
                VALUES (
                    ?, ?, ?,
                    CAST(? AS jsonb),
                    CAST(? AS jsonb),
                    ?, ?
                )
                """,
                snapshotId,
                eventId,
                revision,
                input.toString(),
                protocol.toString(),
                creator.getUserId(),
                OffsetDateTime.now(ZoneOffset.UTC)
        );

        return protocol;
    }

    private String endpoint(UUID eventId) {
        return "/api/v1/events/" + eventId + "/session-protocol";
    }

    private long snapshotCount(UUID eventId) {
        return jdbc.queryForObject(
                """
                SELECT count(*)
                FROM session_protocol_snapshots
                WHERE event_id = ?
                """,
                Long.class,
                eventId
        ).longValue();
    }

    @Test
    void adminReadsLatestRevisionWithoutExposingInput()
            throws Exception {
        var admin = actor("PLATFORM_ADMIN");
        UUID eventId = event(admin);

        // Insert in reverse revision order to test revision-based selection.
        var latest = snapshot(eventId, 2, admin);
        var historical = snapshot(eventId, 1, admin);

        var response = mvc.perform(
                        get(endpoint(eventId))
                                .with(user(admin))
                )
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode actual = json.readTree(response);

        assertEquals(latest, actual);
        assertEquals(2, actual.path("revisionNo").asInt());
        assertFalse(actual.has("input"));
        assertFalse(actual.has("input_json"));
        assertFalse(response.contains("INPUT_MUST_NOT_BE_EXPOSED"));
        assertEquals(2L, snapshotCount(eventId));

        JsonNode savedHistorical = json.readTree(
                jdbc.queryForObject(
                        """
                        SELECT protocol_json::text
                        FROM session_protocol_snapshots
                        WHERE id = ?
                        """,
                        String.class,
                        UUID.fromString(
                                historical.path("snapshotId").asText()
                        )
                )
        );

        assertEquals(historical, savedHistorical);
    }

    @Test
    void adminGetsNotFoundWhenEventHasNoSnapshot()
            throws Exception {
        var admin = actor("PLATFORM_ADMIN");
        UUID eventId = event(admin);

        mvc.perform(
                        get(endpoint(eventId))
                                .with(user(admin))
                )
                .andExpect(status().isNotFound());

        assertEquals(0L, snapshotCount(eventId));
    }

    @Test
    void participantCannotReadSavedSnapshot()
            throws Exception {
        var admin = actor("PLATFORM_ADMIN");
        var participant = actor("PARTICIPANT");
        UUID eventId = event(admin);

        snapshot(eventId, 1, admin);

        mvc.perform(
                        get(endpoint(eventId))
                                .with(user(participant))
                )
                .andExpect(status().isForbidden());

        assertEquals(1L, snapshotCount(eventId));
    }

    @Test
    void unrelatedOrganizerCannotReadSavedSnapshot()
            throws Exception {
        var admin = actor("PLATFORM_ADMIN");
        var outsider = actor("ORGANIZER");
        UUID eventId = event(admin);

        snapshot(eventId, 1, admin);

        mvc.perform(
                        get(endpoint(eventId))
                                .with(user(outsider))
                )
                .andExpect(status().isForbidden());

        assertEquals(1L, snapshotCount(eventId));
    }
}