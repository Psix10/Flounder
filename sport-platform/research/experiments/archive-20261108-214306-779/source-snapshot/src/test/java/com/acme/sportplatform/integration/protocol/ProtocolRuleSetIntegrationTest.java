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
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;

@AutoConfigureMockMvc
class ProtocolRuleSetIntegrationTest extends AbstractPostgresIntegrationTest {
    private static final String URL = "/api/v1/protocol-rule-sets";
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository users;
    @Autowired private ProtocolRuleSetRepository sets;
    @Autowired private ProtocolRuleSetVersionRepository versions;
    @Autowired private ProtocolRuleBlockRepository blocks;

    private PlatformUserPrincipal actor(String role) {
        UserEntity user = new UserEntity();
        user.setEmail("rules-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash("test-password-hash"); user.setStatus("active");
        user = users.saveAndFlush(user);
        return new PlatformUserPrincipal(user.getId(), user.getEmail(), "", "active",
                List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
    private ObjectNode validRequest() throws Exception {
        ObjectNode request = (ObjectNode) json.readTree("""
                {
                  "sportId":"10000000-0000-0000-0000-000000000001",
                  "code":"placeholder",
                  "name":"Rules integration test",
                  "competitionFormat":"INDIVIDUAL",
                  "unitType":"HEAT",
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
        request.put("code", "rule-" + UUID.randomUUID());
        return request;
    }
    private ResultActions create(PlatformUserPrincipal actor, JsonNode request) throws Exception {
        return mvc.perform(post(URL).with(user(actor)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(request)));
    }
    private JsonNode createOk(PlatformUserPrincipal actor, JsonNode request) throws Exception {
        return json.readTree(create(actor, request).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    @Test void shouldCreateDraftAndReadItsExactVersion() throws Exception {
        PlatformUserPrincipal owner = actor("ORGANIZER");
        ObjectNode request = validRequest();
        JsonNode created = createOk(owner, request);
        assertEquals("DRAFT", created.path("status").asText());
        assertEquals(1, created.path("versionNo").asInt());
        assertEquals(4, created.path("blocks").size());
        UUID id = UUID.fromString(created.path("id").asText());
        UUID versionId = UUID.fromString(created.path("ruleSetVersionId").asText());
        assertEquals(owner.getUserId(), sets.findById(id).orElseThrow().getCreatedByUserId());
        assertEquals(id, versions.findById(versionId).orElseThrow().getRuleSetId());
        assertEquals(4, blocks.findByRuleSetVersionIdOrderByOrderIndexAsc(versionId).size());
        mvc.perform(get(URL + "/{id}", id).param("versionId", versionId.toString()).with(user(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ruleSetVersionId").value(versionId.toString()))
                .andExpect(jsonPath("$.blocks[3].config.positionOrder[0]").value(2));
    }

    @Test void shouldRejectDuplicateCodeWithoutCreatingExtraVersionOrBlocks() throws Exception {
        PlatformUserPrincipal owner = actor("ORGANIZER");
        ObjectNode request = validRequest();
        createOk(owner, request);
        long setCount = sets.count(), versionCount = versions.count(), blockCount = blocks.count();
        create(owner, request).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("protocol.rule_set_code_already_exists"));
        assertEquals(setCount, sets.count());
        assertEquals(versionCount, versions.count());
        assertEquals(blockCount, blocks.count());
    }

    @Test void shouldRejectInvalidPositionsWithoutPersistingAnything() throws Exception {
        PlatformUserPrincipal owner = actor("ORGANIZER");
        ObjectNode request = validRequest();
        ((ObjectNode) request.path("blocks").get(3).path("config"))
                .set("positionOrder", json.readTree("[1,1]"));
        long setCount = sets.count(), versionCount = versions.count(), blockCount = blocks.count();
        create(owner, request).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("protocol.invalid_position_order"));
        assertEquals(setCount, sets.count());
        assertEquals(versionCount, versions.count());
        assertEquals(blockCount, blocks.count());
    }

    @Test void shouldDenyOtherOrganizerAndAllowAdminToReadDraft() throws Exception {
        PlatformUserPrincipal owner = actor("ORGANIZER");
        JsonNode created = createOk(owner, validRequest());
        String id = created.path("id").asText();
        mvc.perform(get(URL + "/{id}", id).with(user(actor("ORGANIZER"))))
                .andExpect(status().isForbidden());
        mvc.perform(get(URL + "/{id}", id).with(user(actor("PLATFORM_ADMIN"))))
                .andExpect(status().isOk());
    }

    @Test void shouldRejectVersionBelongingToAnotherRuleSet() throws Exception {
        PlatformUserPrincipal owner = actor("ORGANIZER");
        JsonNode first = createOk(owner, validRequest());
        JsonNode second = createOk(owner, validRequest());
        mvc.perform(get(URL + "/{id}", first.path("id").asText())
                        .param("versionId", second.path("ruleSetVersionId").asText()).with(user(owner)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("protocol.rule_version_not_found"));
    }

    @Test void shouldDenyParticipantCreation() throws Exception {
        PlatformUserPrincipal participant = actor("PARTICIPANT");
        long count = sets.count();
        create(participant, validRequest()).andExpect(status().isForbidden());
        assertEquals(count, sets.count());
    }
}
