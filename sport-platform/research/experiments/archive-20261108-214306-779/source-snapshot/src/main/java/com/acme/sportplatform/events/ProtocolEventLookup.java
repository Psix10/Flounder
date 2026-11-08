package com.acme.sportplatform.events;
import java.util.UUID;
public interface ProtocolEventLookup {
    View getById(UUID eventId);
    record View(UUID sportId, UUID organizationId) { }
}
