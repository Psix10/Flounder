package com.acme.sportplatform.results;

import java.util.List;

public record ResultsCompetitionUnitDraft(
        String label,
        Integer sequenceNumber,
        String status,
        List<ResultsCompetitionUnitEntryDraft> entries
) {
}