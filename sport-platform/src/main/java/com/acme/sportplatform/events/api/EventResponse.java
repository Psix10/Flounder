package com.acme.sportplatform.events.api;

import java.time.OffsetDateTime;
import java.util.UUID;

public record EventResponse(
        UUID id,
        UUID organizationId,
        UUID venueId,
        UUID sportId,
        UUID regulationVersionId,
        String title,
        String description,
        OffsetDateTime registrationOpenAt,
        OffsetDateTime registrationCloseAt,
        OffsetDateTime eventStartAt,
        OffsetDateTime eventEndAt,
        String status,
        String publicSlug,
        String settingsJson,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}