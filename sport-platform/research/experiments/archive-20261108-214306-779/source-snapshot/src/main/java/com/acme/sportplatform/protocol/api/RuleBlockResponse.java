package com.acme.sportplatform.protocol.api;

import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;

public record RuleBlockResponse(

        UUID id,

        Integer orderIndex,

        String executorCode,

        JsonNode config

) {
}