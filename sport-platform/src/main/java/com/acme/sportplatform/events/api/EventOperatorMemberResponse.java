package com.acme.sportplatform.events.api;
import java.time.OffsetDateTime;
import java.util.UUID;
public record EventOperatorMemberResponse(
        UUID id, UUID eventId, UUID operatorUserId, UUID assignedByUserId,
        OffsetDateTime createdAt, String email, String displayName, boolean eligible) {}
