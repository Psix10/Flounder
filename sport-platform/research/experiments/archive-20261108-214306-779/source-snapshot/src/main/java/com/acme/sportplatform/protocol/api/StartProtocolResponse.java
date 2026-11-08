package com.acme.sportplatform.protocol.api;

import java.util.List;
import java.util.UUID;

public record StartProtocolResponse(
        UUID eventDisciplineId,
        int unitCount,
        int assignmentCount,
        List<GeneratedCompetitionUnitResponse> units
) {
}