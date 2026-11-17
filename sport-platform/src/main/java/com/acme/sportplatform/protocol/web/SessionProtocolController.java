package com.acme.sportplatform.protocol.web;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.acme.sportplatform.common.exception.BusinessException;
import com.acme.sportplatform.protocol.api.GenerateSessionProtocolRequest;
import com.acme.sportplatform.protocol.application.GenerateSessionProtocolUseCase;
import com.acme.sportplatform.protocol.application.schedule.GenerateSessionProtocolCommand;
import com.acme.sportplatform.protocol.application.schedule.SessionProtocolGenerationResult;

import jakarta.validation.Valid;

/**
 * Generates a timetable for the explicitly supplied discipline batch.
 * Does not guarantee compatibility with schedules outside that batch.
 */
@RestController
@RequestMapping("/api/v1/events/{eventId}/session-protocol")
public class SessionProtocolController {

    private final GenerateSessionProtocolUseCase generate;

    public SessionProtocolController(
            GenerateSessionProtocolUseCase generate
    ) {
        this.generate = generate;
    }

    @PostMapping("/generate")
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'ORGANIZER')")
    public SessionProtocolGenerationResult generate(
            @PathVariable("eventId") UUID eventId,
            @Valid @RequestBody GenerateSessionProtocolRequest request
    ) {
        GenerateSessionProtocolCommand command;

        try {
            command = request.toCommand(eventId);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    "protocol.invalid_session_request",
                    exception.getMessage() == null
                            ? "Invalid session timing or discipline configuration"
                            : exception.getMessage()
            );
        }

        return generate.execute(command);
    }
}