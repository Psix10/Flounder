package com.acme.sportplatform.protocol.application;

import java.util.List;
import java.util.UUID;

public record ProtocolGenerationResult(
        UUID eventDisciplineId,
        List<GeneratedCompetitionUnit> units
) {
    public int unitCount() {
        return units.size();
    }

    public int assignmentCount() {
        return units.stream()
                .mapToInt(unit -> unit.assignments().size())
                .sum();
    }
}