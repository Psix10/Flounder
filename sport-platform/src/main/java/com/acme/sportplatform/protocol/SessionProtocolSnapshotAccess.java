package com.acme.sportplatform.protocol;
import java.time.OffsetDateTime;
import java.util.UUID;
import com.fasterxml.jackson.databind.JsonNode;
public interface SessionProtocolSnapshotAccess {
    void lockEventBatch(UUID eventId);
    int nextRevision(UUID eventId);
    void save(UUID id, UUID eventId, int revision, JsonNode input, JsonNode protocol,
              UUID actorId, OffsetDateTime createdAt);
}
