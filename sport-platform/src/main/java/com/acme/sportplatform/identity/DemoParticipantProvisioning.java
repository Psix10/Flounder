package com.acme.sportplatform.identity;
import java.util.UUID;
public interface DemoParticipantProvisioning {
    ProfileLookupResult create(UUID eventId, UUID batchId, int ordinal);
}
