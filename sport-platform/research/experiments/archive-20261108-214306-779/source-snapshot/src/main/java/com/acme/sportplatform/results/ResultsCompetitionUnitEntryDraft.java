package com.acme.sportplatform.results;

import java.util.UUID;

public record ResultsCompetitionUnitEntryDraft(
        UUID registrationId,
        Integer laneOrPosition,
        String categoryCode,
        String seedingGroupCode
) {
    public ResultsCompetitionUnitEntryDraft(
            UUID registrationId,
            Integer laneOrPosition
    ) {
        this(registrationId, laneOrPosition, null, null);
    }
}