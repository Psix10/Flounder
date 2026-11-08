package com.acme.sportplatform.integration.regulations;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import com.acme.sportplatform.identity.infrastructure.security.PlatformUserPrincipal;
import com.acme.sportplatform.support.AbstractPostgresIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test")
class UnifiedRegulationIntegrationTest extends AbstractPostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    private PlatformUserPrincipal actor() {
        UUID id = UUID.randomUUID(); String email = "bundle-" + id + "@example.com";
        jdbc.update("insert into users (id,email,password_hash,status,created_at,updated_at) values (?,?,?,'active',now(),now())", id,email,"test-hash");
        return new PlatformUserPrincipal(id,email,"","active",List.of(new SimpleGrantedAuthority("ROLE_ORGANIZER")));
    }
    private Map<String,Object> request(String code, int version) {
        return new LinkedHashMap<>(Map.of(
                "sportId", "10000000-0000-0000-0000-000000000001", "code",code,"name","Unified test",
                "versionNo",version,"conditions",List.of("First condition", "Second condition"),
                "protocol",Map.of("capacity",2,"positionOrder",List.of(1,2),"metricKey","seedTimeSeconds",
                        "heatOrder","SLOWEST_FIRST","partialHeat","FIRST")));
    }
    private long count(String table) { return jdbc.queryForObject("select count(*) from " + table, Long.class); }
    @Test void publishesDocumentRulesAndBindingAndReadsText() throws Exception {
        var owner=actor();
        var response=mvc.perform(post("/api/v1/regulation-configurations").with(user(owner))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(request("bundle-"+UUID.randomUUID(),1))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.document.status").value("published"))
                .andExpect(jsonPath("$.document.binding.ruleVersionId").exists())
                .andExpect(jsonPath("$.document.conditions[0]").value("First condition"))
                .andReturn().getResponse().getContentAsString();
        String id=json.readTree(response).path("document").path("id").asText();
        mvc.perform(get("/api/v1/regulation-configurations/"+id).with(user(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.protocol.capacity").value(2));
        mvc.perform(get("/api/v1/regulation-configurations/"+id).with(user(actor())))
                .andExpect(status().isForbidden());
        var revision=request("ignored",2); revision.put("sourceVersionId",id);
        mvc.perform(post("/api/v1/regulation-configurations").with(user(owner))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(revision)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.document.versionNo").value(2));
        mvc.perform(get("/api/v1/regulation-configurations/"+id).with(user(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.document.versionNo").value(1));
    }
    @Test void rollsBackPublishedRulesWhenDocumentPublicationFails() throws Exception {
        var owner=actor();
        long rules=count("protocol_rule_sets"), templates=count("regulation_templates"), bindings=count("regulation_protocol_bindings");
        mvc.perform(post("/api/v1/regulation-configurations").with(user(owner))
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(request("rollback-"+UUID.randomUUID(),2))))
                .andExpect(status().isBadRequest());
        assertEquals(rules,count("protocol_rule_sets")); assertEquals(templates,count("regulation_templates"));
        assertEquals(bindings,count("regulation_protocol_bindings"));
    }
}
