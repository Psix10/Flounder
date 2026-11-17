package com.acme.sportplatform.registrations;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import com.acme.sportplatform.identity.ProfileLookupResult;

public interface DemoRegistrationAccess {
    record Entry(ProfileLookupResult profile, BigDecimal seedTimeSeconds) { }
    long count(UUID disciplineId);
    List<UUID> createConfirmed(UUID eventId, UUID disciplineId, UUID batchId,
            UUID actorId, List<Entry> entries);
}
