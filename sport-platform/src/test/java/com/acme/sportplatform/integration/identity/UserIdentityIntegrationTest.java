package com.acme.sportplatform.integration.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.acme.sportplatform.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
class UserIdentityIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldCreateAndFetchUser() throws Exception {
        String payload = """
                {
                  "email": "anna@example.com",
                  "password": "secret123",
                  "phone": "+79991112233",
                  "firstName": "Anna",
                  "lastName": "Smirnova",
                  "birthDate": "1998-03-17",
                  "gender": "female",
                  "city": "Moscow",
                  "countryCode": "RU",
                  "clubName": "Wave Club"
                }
                """;

        String response = mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("anna@example.com"))
                .andExpect(jsonPath("$.status").value("active"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String userId = com.jayway.jsonpath.JsonPath.read(response, "$.id");

        mockMvc.perform(get("/api/v1/users/{id}", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("anna@example.com"))
                .andExpect(jsonPath("$.profile.firstName").value("Anna"))
                .andExpect(jsonPath("$.roles[0]").value("participant"));
    }
}