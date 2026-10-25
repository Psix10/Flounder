package com.acme.sportplatform.events.api;

import java.time.OffsetDateTime;
import java.util.UUID;

public record EventOperatorAssignmentResponse(
        UUID id,
        UUID eventId,
        UUID operatorUserId,
        UUID assignedByUserId,
        OffsetDateTime createdAt
) {
}