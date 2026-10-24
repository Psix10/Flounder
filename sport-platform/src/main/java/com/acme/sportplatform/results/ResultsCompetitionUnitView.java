package com.acme.sportplatform.results;

import java.util.List;
import java.util.UUID;

public record ResultsCompetitionUnitView(
        UUID id,
        String label,
        Integer sequenceNumber,
        String status,
        List<ResultsCompetitionUnitEntryView> entries
) {
}