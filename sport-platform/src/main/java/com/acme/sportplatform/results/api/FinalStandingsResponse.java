package com.acme.sportplatform.results.api;
import java.time.OffsetDateTime;
import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;
public record FinalStandingsResponse(UUID id, UUID eventId, UUID eventDisciplineId,
        int revision, String status, OffsetDateTime confirmedAt, UUID confirmedBy,
        String confirmationNote, JsonNode standings, OffsetDateTime revokedAt,
        UUID revokedBy, String revocationReason) { }
