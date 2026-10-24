package com.acme.sportplatform.results;

import java.util.UUID;

public record ResultsCompetitionUnitEntryView(
        UUID id,
        UUID registrationId,
        Integer laneOrPosition
) {
}