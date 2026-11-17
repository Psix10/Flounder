package com.acme.sportplatform.protocol.web;

import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.acme.sportplatform.protocol.application.GetSessionProtocolUseCase;
import com.fasterxml.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/events/{eventId}/session-protocol")
public class SessionProtocolQueryController {

    private final GetSessionProtocolUseCase getSessionProtocol;

    public SessionProtocolQueryController(
            GetSessionProtocolUseCase getSessionProtocol
    ) {
        this.getSessionProtocol = getSessionProtocol;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'ORGANIZER')")
    public JsonNode getLatest(
            @PathVariable("eventId") UUID eventId
    ) {
        return getSessionProtocol.execute(eventId);
    }
}