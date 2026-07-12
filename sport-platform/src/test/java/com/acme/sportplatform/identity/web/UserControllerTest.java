package com.acme.sportplatform.identity.web;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;

@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldCreateUser() throws Exception {
        String payload = """
                {
                  "email": "ivan@example.com",
                  "password": "secret123",
                  "phone": "+79990000000",
                  "firstName": "Ivan",
                  "lastName": "Petrov",
                  "birthDate": "1995-05-12",
                  "gender": "male",
                  "city": "Moscow",
                  "countryCode": "RU",
                  "clubName": "Dolphin Club"
                }
                """;

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("ivan@example.com"))
                .andExpect(jsonPath("$.status").value("active"));
    }

    @Test
    void shouldReturnValidationErrorForInvalidRequest() throws Exception {
        String payload = """
                {
                  "email": "bad-email",
                  "password": "",
                  "firstName": "",
                  "lastName": "Petrov"
                }
                """;

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));
    }
}