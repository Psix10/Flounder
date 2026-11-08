package com.acme.sportplatform.configuration;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

import com.acme.sportplatform.identity.infrastructure.persistence.entity.UserEntity;
import com.acme.sportplatform.identity.infrastructure.persistence.repository.UserRepository;
import com.acme.sportplatform.identity.infrastructure.security.PlatformUserPrincipal;
import com.acme.sportplatform.support.AbstractPostgresIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@AutoConfigureMockMvc
class CategoryRegulationConfigurationIntegrationTest extends AbstractPostgresIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @Autowired private UserRepository users;
    @Autowired private JdbcTemplate jdbc;

    private PlatformUserPrincipal actor() {
        var entity = new UserEntity();
        entity.setEmail("category-configuration-" + UUID.randomUUID() + "@example.com");
        entity.setPasswordHash("test-password-hash");
        entity.setStatus("active");
        entity = users.saveAndFlush(entity);
        return new PlatformUserPrincipal(entity.getId(), entity.getEmail(), "", "active",
                List.of(new SimpleGrantedAuthority("ROLE_ORGANIZER")));
    }

    private ObjectNode request(boolean separate) throws Exception {
        ObjectNode request = (ObjectNode) json.readTree("""
                {"sportId":"10000000-0000-0000-0000-000000000001",
                 "code":"placeholder","name":"Category integration test","versionNo":1,
                 "conditions":["Test only"],"protocol":{
                   "capacity":2,"positionOrder":[1,2],"metricKey":"seedTimeSeconds",
                   "heatOrder":"SLOWEST_FIRST","partialHeat":"FIRST","categories":[
                     {"code":"MEN","seedingGroupCode":"OPEN","eligibility":{"allowedGenders":["MALE"]}},
                     {"code":"WOMEN","seedingGroupCode":"OPEN","eligibility":{"allowedGenders":["FEMALE"]}}
                   ]}}
                """);
        request.put("code", "CATEGORY-" + UUID.randomUUID());
        if (separate) {
            ((ObjectNode) request.path("protocol").path("categories").get(0)).put("seedingGroupCode", "MEN");
            ((ObjectNode) request.path("protocol").path("categories").get(1)).put("seedingGroupCode", "WOMEN");
        }
        return request;
    }

    private JsonNode publish(PlatformUserPrincipal actor, JsonNode request) throws Exception {
        return json.readTree(mvc.perform(post("/api/v1/regulation-configurations")
                .with(user(actor)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(request)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode read(PlatformUserPrincipal actor, String id) throws Exception {
        return json.readTree(mvc.perform(get("/api/v1/regulation-configurations/{id}", id)
                .with(user(actor))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    @Test
    void shouldPersistCategoriesAndReturnThemThroughConfigurationApi() throws Exception {
        var owner = actor();
        var request = request(false);
        var saved = publish(owner, request);
        var loaded = read(owner, saved.path("document").path("id").asText());
        var expected = request.path("protocol").path("categories");
        assertEquals(expected, loaded.path("protocol").path("categories"));
        UUID ruleVersion = UUID.fromString(loaded.path("document").path("binding").path("ruleVersionId").asText());
        String stored = jdbc.queryForObject("""
                SELECT config_json::text FROM protocol_rule_blocks
                WHERE rule_set_version_id = ? AND executor_code = 'GROUP_BY_CAPACITY'
                """, String.class, ruleVersion);
        assertEquals(expected, json.readTree(stored).path("categories"));
    }

    @Test
    void shouldKeepPreviousVersionCategoriesWhenPublishingNewVersion() throws Exception {
        var owner = actor();
        var request = request(true);
        var first = publish(owner, request);
        String sourceId = first.path("document").path("id").asText();
        var original = read(owner, sourceId);
        var next = request.deepCopy();
        next.put("sourceVersionId", sourceId);
        next.put("versionNo", 2);
        for (var category : next.path("protocol").path("categories")) {
            ((ObjectNode) category).put("seedingGroupCode", "OPEN");
        }
        var second = publish(owner, next);
        assertEquals(original, read(owner, sourceId));
        assertEquals(1, original.path("document").path("versionNo").asInt());
        assertEquals(2, second.path("document").path("versionNo").asInt());
        assertNotEquals(first.path("document").path("binding"), second.path("document").path("binding"));
        assertEquals(next.path("protocol").path("categories"),
                read(owner, second.path("document").path("id").asText()).path("protocol").path("categories"));
    }

    @Test
    void shouldRejectDuplicateCategoriesWithoutCreatingRegulationDocument() throws Exception {
        var owner = actor();
        var request = request(false);
        ((ObjectNode) request.path("protocol").path("categories").get(1)).put("code", "MEN");
        mvc.perform(post("/api/v1/regulation-configurations").with(user(owner))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("protocol.invalid_rule_config"));
        Long count = jdbc.queryForObject("SELECT count(*) FROM regulation_templates WHERE upper(code) = upper(?)",
                Long.class, request.path("code").asText());
        assertEquals(Long.valueOf(0), count);
    }
}
