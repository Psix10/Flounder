package com.acme.sportplatform.competition.api;
import java.util.UUID;
public record OperatorDisciplineResponse(UUID id, UUID eventId, String name, String status,
        String competitionFormat, String unitType, String resultType) {}
