package com.acme.sportplatform.protocol.web;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.acme.sportplatform.common.web.GlobalExceptionHandler;
import com.acme.sportplatform.protocol.application.GenerateSessionProtocolUseCase;
import com.acme.sportplatform.protocol.application.schedule.GenerateSessionProtocolCommand;
import com.acme.sportplatform.protocol.application.schedule.SessionProtocolGenerationResult;
import com.acme.sportplatform.protocol.domain.schedule.ProtocolScheduleModel.Origin;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

class SessionProtocolControllerTest {

    private static final UUID EVENT_ID = UUID.fromString(
            "10000000-0000-4000-8000-000000000001"
    );
    private static final UUID SESSION_ID = UUID.fromString(
            "20000000-0000-4000-8000-000000000001"
    );
    private static final UUID DISCIPLINE_ID = UUID.fromString(
            "30000000-0000-4000-8000-000000000001"
    );
    private static final UUID SNAPSHOT_ID = UUID.fromString(
            "40000000-0000-4000-8000-000000000001"
    );

    private final ObjectMapper json = new ObjectMapper();

    private GenerateSessionProtocolUseCase generate;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        generate = mock(GenerateSessionProtocolUseCase.class);

        var validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mvc = MockMvcBuilders
                .standaloneSetup(new SessionProtocolController(generate))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    private ObjectNode validRequest() throws Exception {
        return (ObjectNode) json.readTree("""
                {
                  "regenerate": false,
                  "timingPolicy": {
                    "minimumRestSeconds": 600,
                    "changeoverSeconds": 30,
                    "sourceDescription": "Organizer-confirmed policy"
                  },
                  "sessions": [
                    {
                      "id": "%s",
                      "start": "2026-11-22T09:30:00+03:00",
                      "end": "2026-11-22T12:30:00+03:00",
                      "breaks": []
                    }
                  ],
                  "disciplines": [
                    {
                      "disciplineId": "%s",
                      "ruleSetVersionId": null,
                      "sessionId": "%s",
                      "heatDurationSeconds": 120,
                      "durationBasis": "Organizer planning estimate"
                    }
                  ]
                }
                """.formatted(
                SESSION_ID,
                DISCIPLINE_ID,
                SESSION_ID
        ));
    }

    private String endpoint() {
        return "/api/v1/events/" + EVENT_ID
                + "/session-protocol/generate";
    }

    @Test
    void generatesAndMapsRequestToInternalCommand() throws Exception {
        when(generate.execute(any(GenerateSessionProtocolCommand.class)))
                .thenReturn(new SessionProtocolGenerationResult(
                        SNAPSHOT_ID,
                        EVENT_ID,
                        1,
                        List.of(),
                        List.of()
                ));

        mvc.perform(post(endpoint())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshotId")
                        .value(SNAPSHOT_ID.toString()))
                .andExpect(jsonPath("$.eventId")
                        .value(EVENT_ID.toString()))
                .andExpect(jsonPath("$.revisionNo").value(1));

        var captor = ArgumentCaptor.forClass(
                GenerateSessionProtocolCommand.class
        );
        verify(generate).execute(captor.capture());

        var command = captor.getValue();

        assertEquals(EVENT_ID, command.eventId());
        assertEquals(false, command.regenerate());
        assertEquals(
                Origin.ORGANIZER_CONFIRMED,
                command.timingPolicy().origin()
        );
        assertEquals(
                Duration.ofSeconds(600),
                command.timingPolicy().minimumRest()
        );
        assertEquals(
                Duration.ofSeconds(30),
                command.timingPolicy().changeover()
        );
        assertEquals(
                Instant.parse("2026-11-22T06:30:00Z"),
                command.sessions().get(0).window().start()
        );
        assertEquals(
                DISCIPLINE_ID,
                command.disciplines().get(0).disciplineId()
        );
        assertEquals(
                Duration.ofSeconds(120),
                command.disciplines().get(0).heatDuration()
        );
        assertNull(command.disciplines().get(0).ruleSetVersionId());
    }

    @Test
    void rejectsMissingTimingPolicy() throws Exception {
        var request = validRequest();
        request.remove("timingPolicy");

        mvc.perform(post(endpoint())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));

        verifyNoInteractions(generate);
    }

    @Test
    void rejectsNonPositiveHeatDuration() throws Exception {
        var request = validRequest();
        ((ObjectNode) request.path("disciplines").get(0))
                .put("heatDurationSeconds", 0);

        mvc.perform(post(endpoint())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation_error"));

        verifyNoInteractions(generate);
    }

    @Test
    void rejectsReversedSessionWindow() throws Exception {
        var request = validRequest();
        ((ObjectNode) request.path("sessions").get(0))
                .put("end", "2026-11-22T09:00:00+03:00");

        mvc.perform(post(endpoint())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("protocol.invalid_session_request"));

        verifyNoInteractions(generate);
    }

    @Test
    void rejectsUnknownSessionReference() throws Exception {
        var request = validRequest();
        ((ObjectNode) request.path("disciplines").get(0))
                .put(
                        "sessionId",
                        "50000000-0000-4000-8000-000000000001"
                );

        mvc.perform(post(endpoint())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("protocol.invalid_session_request"));

        verifyNoInteractions(generate);
    }

    @Test
    void rejectsMalformedJson() throws Exception {
        mvc.perform(post(endpoint())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code")
                        .value("invalid_request_body"));

        verifyNoInteractions(generate);
    }
}