package com.acme.sportplatform.integration.protocol;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import org.springframework.test.web.servlet.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.acme.sportplatform.support.AbstractPostgresIntegrationTest;
import com.acme.sportplatform.identity.infrastructure.persistence.entity.UserEntity;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.UserRepository;
import com.acme.sportplatform.identity.infrastructure.security.PlatformUserPrincipal;
import com.acme.sportplatform.protocol.infrastructure.jpa.*;
import com.acme.sportplatform.protocol.domain.ProtocolRuleSetStatus;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;

@AutoConfigureMockMvc
class ProtocolRuleSetPublicationIntegrationTest extends AbstractPostgresIntegrationTest {
    private static final String URL = "/api/v1/protocol-rule-sets";
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository users;
    @Autowired private ProtocolRuleSetVersionRepository versions;
    @Autowired private ProtocolRuleBlockRepository blocks;

    private PlatformUserPrincipal actor(String role) {
        UserEntity entity = new UserEntity();
        entity.setEmail("publish-rules-" + UUID.randomUUID() + "@example.com");
        entity.setPasswordHash("test-password-hash"); entity.setStatus("active");
        entity = users.saveAndFlush(entity);
        return new PlatformUserPrincipal(entity.getId(), entity.getEmail(), "", "active",
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }

    private JsonNode create(PlatformUserPrincipal owner) throws Exception {
        ObjectNode request = (ObjectNode) json.readTree("""
                {
                  "sportId":"10000000-0000-0000-0000-000000000001",
                  "code":"placeholder","name":"Publication test",
                  "competitionFormat":"INDIVIDUAL","unitType":"HEAT",
                  "blocks":[
                    {"orderIndex":0,"executorCode":"ELIGIBILITY_CONFIRMED_ONLY","config":{}},
                    {"orderIndex":1,"executorCode":"SEED_BY_METRIC_ASC_NULLS_LAST",
                     "config":{"metricKey":"seedTimeSeconds"}},
                    {"orderIndex":2,"executorCode":"GROUP_BY_CAPACITY",
                     "config":{"capacity":2,"unitLabelPrefix":"Heat"}},
                    {"orderIndex":3,"executorCode":"ASSIGN_CENTER_OUT",
                     "config":{"positionOrder":[2,1]}}
                  ]
                }
                """);
        request.put("code", "publish-" + UUID.randomUUID());
        return json.readTree(mvc.perform(post(URL).with(user(owner))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(request)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private ResultActions publish(JsonNode created, PlatformUserPrincipal actor) throws Exception {
        return mvc.perform(post(URL + "/{id}/versions/{versionId}/publish",
                created.path("id").asText(), created.path("ruleSetVersionId").asText())
                .with(user(actor)));
    }

    @Test void shouldPublishOwnedDraftAndReadPublishedVersion() throws Exception {
        PlatformUserPrincipal owner = actor("ORGANIZER");
        JsonNode created = create(owner);
        UUID versionId = UUID.fromString(created.path("ruleSetVersionId").asText());
        long before = versions.findById(versionId).orElseThrow().getVersion();
        publish(created, owner).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.publishedAt").exists());
        ProtocolRuleSetVersionEntity version = versions.findById(versionId).orElseThrow();
        assertEquals(ProtocolRuleSetStatus.PUBLISHED, version.getStatus());
        assertNotNull(version.getPublishedAt());
        assertEquals(Long.valueOf(before + 1), version.getVersion());
        mvc.perform(get(URL + "/{id}", created.path("id").asText()).with(user(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test void shouldRejectRepeatedPublicationWithoutChangingVersionOrTimestamp() throws Exception {
        PlatformUserPrincipal owner = actor("ORGANIZER");
        JsonNode created = create(owner);
        publish(created, owner).andExpect(status().isOk());
        UUID id = UUID.fromString(created.path("ruleSetVersionId").asText());
        ProtocolRuleSetVersionEntity before = versions.findById(id).orElseThrow();
        Long oldVersion = before.getVersion();
        java.time.OffsetDateTime oldTime = before.getPublishedAt();
        publish(created, owner).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("protocol.rule_version_already_published"));
        ProtocolRuleSetVersionEntity after = versions.findById(id).orElseThrow();
        assertEquals(oldVersion, after.getVersion());
        assertEquals(oldTime, after.getPublishedAt());
    }

    @Test void shouldDenyOtherOrganizerPublication() throws Exception {
        JsonNode created = create(actor("ORGANIZER"));
        publish(created, actor("ORGANIZER")).andExpect(status().isForbidden());
        assertEquals(ProtocolRuleSetStatus.DRAFT, versions.findById(
                UUID.fromString(created.path("ruleSetVersionId").asText())).orElseThrow().getStatus());
    }

    @Test void shouldAllowAdminToPublishAnotherOrganizersVersion() throws Exception {
        JsonNode created = create(actor("ORGANIZER"));
        publish(created, actor("PLATFORM_ADMIN")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test void shouldRejectVersionFromAnotherRuleSet() throws Exception {
        PlatformUserPrincipal owner = actor("ORGANIZER");
        JsonNode first = create(owner), second = create(owner);
        mvc.perform(post(URL + "/{id}/versions/{versionId}/publish",
                        first.path("id").asText(), second.path("ruleSetVersionId").asText())
                        .with(user(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("protocol.rule_version_not_found"));
    }

    @Test void shouldRevalidatePersistedBlocksBeforePublishing() throws Exception {
        PlatformUserPrincipal owner = actor("ORGANIZER");
        JsonNode created = create(owner);
        UUID id = UUID.fromString(created.path("ruleSetVersionId").asText());
        ProtocolRuleBlockEntity assignment = blocks.findByRuleSetVersionIdOrderByOrderIndexAsc(id).get(3);
        ObjectNode invalidConfig = json.createObjectNode();
        invalidConfig.putArray("positionOrder").add(1).add(1);
        assignment.setConfig(invalidConfig);
        blocks.saveAndFlush(assignment);
        publish(created, owner).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("protocol.invalid_position_order"));
        ProtocolRuleSetVersionEntity version = versions.findById(id).orElseThrow();
        assertEquals(ProtocolRuleSetStatus.DRAFT, version.getStatus());
        assertNull(version.getPublishedAt());
    }
}
