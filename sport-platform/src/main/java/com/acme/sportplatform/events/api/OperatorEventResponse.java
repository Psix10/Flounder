package com.acme.sportplatform.events.api;
import java.time.OffsetDateTime;
import java.util.UUID;
public record OperatorEventResponse(UUID id, String title, String status,
        OffsetDateTime eventStartAt, OffsetDateTime eventEndAt, String publicSlug) {}
