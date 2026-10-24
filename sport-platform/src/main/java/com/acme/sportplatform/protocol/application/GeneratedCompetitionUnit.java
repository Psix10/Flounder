package com.acme.sportplatform.protocol.application;

import java.util.List;
import java.util.UUID;

public record GeneratedCompetitionUnit(
        UUID competitionUnitId,
        String label,
        Integer sequenceNumber,
        List<GeneratedAssignment> assignments
) {
}