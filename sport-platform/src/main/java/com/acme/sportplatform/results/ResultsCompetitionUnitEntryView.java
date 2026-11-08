package com.acme.sportplatform.results;

import java.util.UUID;

public record ResultsCompetitionUnitEntryView(
        UUID id,
        UUID registrationId,
        Integer laneOrPosition,
        String categoryCode,
        String seedingGroupCode
) {
    public ResultsCompetitionUnitEntryView(
            UUID id,
            UUID registrationId,
            Integer laneOrPosition
    ) {
        this(id, registrationId, laneOrPosition, null, null);
    }
}