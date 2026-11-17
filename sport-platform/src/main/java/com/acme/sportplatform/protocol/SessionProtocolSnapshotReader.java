package com.acme.sportplatform.protocol;

import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

public interface SessionProtocolSnapshotReader {

    Optional<JsonNode> findLatestProtocol(UUID eventId);
}