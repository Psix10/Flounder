package com.acme.sportplatform.identity;

import java.time.LocalDate;
import java.util.UUID;

public interface DemoParticipantProvisioning {
    record Fixture(String gender, LocalDate birthDate) {
        public Fixture {
            if (!"MALE".equals(gender) && !"FEMALE".equals(gender)) {
                throw new IllegalArgumentException("Demo gender must be MALE or FEMALE");
            }
            if (birthDate == null || birthDate.isAfter(LocalDate.now())) {
                throw new IllegalArgumentException("Demo birth date must be present and not in the future");
            }
        }
    }

    ProfileLookupResult create(UUID eventId, UUID batchId, int ordinal);

    ProfileLookupResult create(UUID eventId, UUID batchId, int ordinal, Fixture fixture);
}
