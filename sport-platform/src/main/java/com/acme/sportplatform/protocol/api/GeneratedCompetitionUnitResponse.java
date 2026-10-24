package com.acme.sportplatform.protocol.api;

import java.util.List;
import java.util.UUID;

public record GeneratedCompetitionUnitResponse(
        UUID competitionUnitId,
        String label,
        Integer sequenceNumber,
        List<GeneratedAssignmentResponse> assignments
) {
}