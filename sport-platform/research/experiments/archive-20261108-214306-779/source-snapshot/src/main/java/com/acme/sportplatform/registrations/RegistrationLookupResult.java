package com.acme.sportplatform.registrations;

import java.util.UUID;

public record RegistrationLookupResult(
        UUID id,
        UUID participantUserId,
        UUID eventDisciplineId,
        String status,
        String participantSnapshot,
        String registrationMeta,
        UUID participantProfileId
) {
    /** Compatibility constructor: profile identity is unknown, never inferred from other IDs. */
    public RegistrationLookupResult(UUID id, UUID participantUserId, UUID eventDisciplineId,
                                    String status, String participantSnapshot, String registrationMeta) {
        this(id, participantUserId, eventDisciplineId, status, participantSnapshot, registrationMeta, null);
    }
}
